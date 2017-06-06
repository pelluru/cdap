:: ##############################################################################
:: ##
:: ## Copyright (c) 2016-2017 Cask Data, Inc.
:: ##
:: ## Licensed under the Apache License, Version 2.0 (the "License"); you may not
:: ## use this file except in compliance with the License. You may obtain a copy
:: ## of the License at
:: ##
:: ## http://www.apache.org/licenses/LICENSE-2.0
:: ##
:: ## Unless required by applicable law or agreed to in writing, software
:: ## distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
:: ## WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
:: ## License for the specific language governing permissions and limitations
:: ## under the License.
:: ##
:: ##############################################################################

@echo OFF
REM Set the base directory
for %%i in ("%~dp0..\") do (SET $APP_HOME=%%~dpi)

REM Set path for curl.exe
SET "ORIG_PATH=%PATH%"
SET "PATH=%PATH%;%APP_HOME%\..\..\libexec\bin"

REM Process access token
set $ACCESS_TOKEN=
set $ACCESS_TOKEN_FILE=%HOMEPATH%\.cdap.accesstoken
if exist %$ACCESS_TOKEN_FILE% set /p $ACCESS_TOKEN=<%$ACCESS_TOKEN_FILE%

set $STREAM=events
set $ENDPOINT=v3/namespaces/default/streams/%$STREAM%

SETLOCAL EnableDelayedExpansion

REM Set parameters
set $EVENTS=%1
set $DELAY=%2
set $HOST=%3
set $ERROR=

if not DEFINED $EVENTS set $ERROR=Events must be set
if DEFINED $ERROR goto :USAGE

if not DEFINED $DELAY set $ERROR=Delay must be set
if DEFINED $ERROR goto :USAGE

GOTO PROGRAM

:USAGE
SET $PROGRAM_NAME=%0
echo Tool for sending events to the '%$STREAM%' stream
echo Usage: %$PROGRAM_NAME% events delay [host]
echo:
echo Options
echo     events    How many events to send (min 1)
echo     delay     How many (integer) seconds to sleep between each event-call (min 1)
echo     host      Specifies the host that CDAP is running on (default: localhost)
echo:
if DEFINED $ERROR echo Error: !$ERROR!
set $ERROR=
GOTO FINALLY

:PROGRAM
if DEFINED $HOST set $GATEWAY=!$HOST!
if not DEFINED $GATEWAY set $GATEWAY=localhost

echo Sending !$EVENTS! events with delay !$DELAY! to the stream '!$STREAM!' at !$GATEWAY!
for /L %%G IN (1 1 !$EVENTS!) DO (
    set /a "$BODY=(!RANDOM!*100/32768)+1"
    for /f %%a in ('copy /Z "%~f0" nul') do set "CR=%%a"
    REM Creates a carriage return (CR) character so that the set will overwrite itself
    set /P "=Sending event %%G '!$BODY!' (press CTRL+C to cancel)!CR!" <nul
    set $AUTH=
    if DEFINED $ACCESS_TOKEN set $AUTH=-H "Authorization: Bearer !$ACCESS_TOKEN!"
    curl !$AUTH! -X POST -d"!$BODY!" "http://!$GATEWAY!:11015/%$ENDPOINT%"
    set /a $PING_DELAY=!$DELAY!+1
    ping -n !$PING_DELAY! localhost >nul
)
GOTO FINALLY

:FINALLY
SET "PATH=%ORIG_PATH%"
