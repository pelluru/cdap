/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

angular.module(PKG.name+'.commons')
.directive('myEmbeddedSchemaSelector', function () {
  return {
    restrict: 'E',
    templateUrl: 'complex-schema/embedded-schema-selector/embedded-schema-selector.html',
    scope: {
      type: '=',
      displayType: '=',
      parentFormatOutput: '&',
      isDisabled: '='
    },
    bindToController: true,
    controller: function (SchemaHelper) {
      var vm = this;
      vm.checkComplexType = SchemaHelper.checkComplexType;
      vm.expanded = true;
    },
    controllerAs: 'Embedded'
  };
});
