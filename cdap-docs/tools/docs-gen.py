#!/usr/bin/env python
# -*- coding: utf-8 -*-

#
# Copyright © 2014-2017 Cask Data, Inc.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
# Used to generate documentation media files from .rst sources.
#
# REQUIRES PYTHON 2.7.x
#
# Currently, uses rst2pdf to generate PDFs.
# To be enhanced later to include using landslide to generate slides.
#
# PDF Generation
# --------------
# Pre-processes the .rst file into a temp file, and then runs the temp file through rst2pdf.
# The pre-processing allows custom hints to be placed in the .rst file that can specify
# and control the PDF output.
#
# Current hints are listed below in section marked "Current Hints".
#
# Any hints given on the command line take precedence over the internal hints.
#
# Note that in the examples below, the output directory is given relative to the location of the input directory,
# which is given relative to the script location.
#
# PDF generation examples:
# python doc-gen.py -g pdf -o ../../../developer-manual/licenses-pdf/cdap-enterprise-dependencies.pdf ../developer-manual/source/licenses/cdap-enterprise-dependencies.rst
# python doc-gen.py -g pdf -o ../../../developer-manual/licenses-pdf/cdap-level-1-dependencies.pdf    ../developer-manual/source/licenses/cdap-level-1-dependencies.rst
# python doc-gen.py -g pdf -o ../../../developer-manual/licenses-pdf/cdap-local-sandbox-dependencies.pdf ../developer-manual/source/licenses/cdap-local-sandbox-dependencies.rst
#

VERSION = "0.0.4"
DEFAULT_OUTPUT_PDF_FILE = "output.pdf"
DEFAULT_GENERATE = "pdf"
TEMP_FILE_SUFFIX = "_temp"

REST_EDITOR             = ".. reST Editor: "
RST2PDF                 = ".. rst2pdf: "

# Current Hints
RST2PDF_BUILD           = ".. rst2pdf: build " # Sets the build output directory
RST2PDF_CONFIG          = ".. rst2pdf: config " # Sets the config file used
RST2PDF_NAME            = ".. rst2pdf: name " # Sets the output file name
RST2PDF_STYLESHEETS     = ".. rst2pdf: stylesheets " # Sets the stylesheet used
RST2PDF_CUT_START       = ".. rst2pdf: CutStart" # Marks the start of an ignored section
RST2PDF_CUT_STOP        = ".. rst2pdf: CutStop" # Marks the end of an ignored section
RST2PDF_PAGE_BREAK      = ".. rst2pdf: PageBreak" # Marks the insertion of pagebreak
RST2PDF_PAGE_BREAK_TEXT = """.. raw:: pdf

	PageBreak"""

DIRECTIVE_HIGHLIGHT      = ".. highlight::" # Highlight directive, not supported by rst2pdf

import os
import subprocess
import sys
from optparse import OptionParser


def parse_options():
    """ Parses args options.
    """

    parser = OptionParser(
        usage="%prog [options] input.rst",
        description="Generates HTML, PDF or Slides from an reST-formatted file (currently limited to PDF)")

    parser.add_option(
        "-b", "--buildversion",
        dest="build_version",
        help="Version of CDAP",
        default="")

    parser.add_option(
        "-o", "--output",
        dest="output_file",
        help="The path to the output file: .html or "
             ".pdf extensions allowed (default: %s)" % DEFAULT_OUTPUT_PDF_FILE,
        metavar="FILE",
        default=DEFAULT_OUTPUT_PDF_FILE)

    parser.add_option(
        "-g", "--generate",
        dest="generate",
        help="One of html, pdf, or slides "
             "(default %s)" % DEFAULT_GENERATE,
        default=DEFAULT_GENERATE)

    parser.add_option(
        "-v", "--version",
        action="store_true",
        dest="version",
        help="Version of this software",
        default=False)

    (options, args) = parser.parse_args()

    options.generate = options.generate.lower()

    if options.version:
        print "Version: %s" % VERSION
        sys.exit(1)

    if not args:
        parser.print_help()
        sys.exit(1)

    return options, args[0]


def log(message, type):
    """Basic logger, print output directly to stdout and errors to stderr.
    """
    (sys.stdout if type == 'notice' else sys.stderr).write(message + "\n")


def process_pdf(input_file, options):
    func_name = "process_pdf"
    output = ""
    config = ""
    name = ""
    stylesheets = ""
    print "input_file: %s" % input_file
    f = open(input_file,'r')
    lines = []
    rst2pdf_cut = False
    for line in f:
        if line_starts_with(line, RST2PDF_CUT_START):
            rst2pdf_cut = True
        elif line_starts_with(line, RST2PDF_CUT_STOP):
            rst2pdf_cut = False
        elif rst2pdf_cut:
            continue
        elif line_starts_with(line, REST_EDITOR):
            # Ignore all reST Editor directives
            continue
        elif line_starts_with(line, DIRECTIVE_HIGHLIGHT):
            # Ignore Highlight directive
            continue
        elif line_starts_with(line, RST2PDF_BUILD):
            print line
            build = line_right_end( line, RST2PDF_BUILD)
            if build[-1:] != "/":
                build = build + "/"
            print "build: %s" % build
        elif line_starts_with(line, RST2PDF_NAME):
            print line
            name = line_right_end( line, RST2PDF_NAME)
            print "name: %s" % name
        elif line_starts_with(line, RST2PDF_CONFIG):
            print line
            config = line_right_end( line, RST2PDF_CONFIG)
            print "config: %s" % config
        elif line_starts_with(line, RST2PDF_STYLESHEETS):
            print line
            stylesheets = line_right_end( line, RST2PDF_STYLESHEETS)
            print "stylesheets: %s" % stylesheets
        elif line_starts_with(line,  RST2PDF_PAGE_BREAK):
            lines.append(RST2PDF_PAGE_BREAK_TEXT)
        elif line_starts_with(line, RST2PDF):
            lines.append(line_right_end(line, RST2PDF))
        else:
            lines.append(line.strip('\n'))

    # Set paths
    source_path = os.path.dirname(os.path.abspath(__file__))

    if not os.path.isabs(input_file):
        input_file = os.path.join(source_path, input_file)
        if not os.path.isfile(input_file):
            raise Exception(func_name, '"input_file" not a valid path: %s' % input_file)

    if options.output_file: # If output file specified on command line, use it
        output = options.output_file
    elif output and name: # If output file name and build location specified in file, use them
        output = os.path.join(output, name)

    output = get_absolute_path(output, "output", input_file, func_name)
    config = get_absolute_path(config, "config", input_file, func_name)
    stylesheets = get_absolute_path(stylesheets, "stylesheets", input_file, func_name)

    # Write output to temp file
    temp_file = input_file+TEMP_FILE_SUFFIX
    if not os.path.isabs(temp_file):
        raise Exception(func_name, '"temp_file" not a valid path: %s' % temp_file)
    temp = open(temp_file,'w')
    for line in lines:
        temp.write(line+'\n')
    if options.build_version: # If a build version was specified on command-line, use it
        line = ".. |version| replace:: %s" % options.build_version
        temp.write('\n'+line+'\n')
    temp.close()
    print "Completed parsing input file"

    # Generate PDF
#     rst2pdf
#     --config="/Users/*/*/cdap/docs/developer-manual/source/_templates/pdf-config"
#     --stylesheets="/Users/*/*/cdap/docs/developer-manual/source/_templates/pdf-stylesheet"
#     -o "/Users/*/*/cdap/docs/developer-manual/build-pdf/rest2.pdf"
#     "/Users/*/*/cdap/docs/developer-manual/source/rest.rst_temp”

    command = 'rst2pdf --config="%s" --stylesheets="%s" -o "%s" %s' % (config, stylesheets, output, temp_file)
    print "command: %s" % command
    try:
        output = subprocess.check_output(command, shell=True, stderr=subprocess.STDOUT,)
    except:
        raise Exception(func_name, 'output: %s' % output)

    if len(output)==0:
        os.remove(temp_file)
    else:
        print output

    print "Completed %s" % func_name

#
# Utility functions
#

def line_starts_with(line, left):
    return bool(line[:len(left)] == left)


def line_right_end(line, left):
    # Given a line of text (that may end with a carriage return)
    # and a snip at the start,
    # return everything from the end of snip onwards, except for the trailing return
    t = line[len(left):]
    return t.strip('\n')

def get_absolute_path(file_path, name, input_file, func):
    if not os.path.isabs(file_path):
        file_path = os.path.join(os.path.dirname(input_file), file_path)
        if not os.path.isdir(os.path.dirname(file_path)):
            raise Exception(func, '"%s" not a valid path: %s' % (name, file_path))
    return file_path

def startup_checks():
    if sys.version_info < (2, 7):
        raise Exception("Must use python 2.7 or greater")

#
# Main function
#

def main():
    """ Main program entry point.
    """

    try:
        startup_checks()
        options, input_file = parse_options()
        options.logger = log
        if options.generate == "pdf":
            print "PDF generation..."
            process_pdf(input_file, options)
        elif options.generate == "html":
            print "HTML generation not implemented"
        elif options.generate == "slides":
            print "Slides generation not implemented"
        else:
            print "Unknown generation type: %s" % options.generate
            sys.exit(1)
    except Exception, e:
        sys.stderr.write("Error: %s\n" % e)
        sys.exit(1)


if __name__ == '__main__':
    main()
