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

/**
 * This class is responsible for controlling the Metadata View in Tracker
 * entity detai page.
 **/

 /*
   TODO:
     - Add support for Stream Views
     - What to do with externalDataset type
 */

class TrackerMetadataController {
  constructor($state, myTrackerApi, $scope, myAlertOnValium, $timeout, $q) {
    this.$state = $state;
    this.myTrackerApi = myTrackerApi;
    this.$scope = $scope;
    this.myAlertOnValium = myAlertOnValium;
    this.$timeout = $timeout;
    this.$q = $q;
    this.duplicateTag = false;

    this.propertyInput = {
      key: '',
      value: ''
    };

    let entitySplit = this.$state.params.entityType.split(':');
    this.entityType = entitySplit;

    let params = {
      scope: this.$scope,
      namespace: this.$state.params.namespace,
      entityType: entitySplit[0],
    };

    let metadataApi;

    if (entitySplit.length > 1) {
      params.entityId = entitySplit[1];
      params.entityType = 'streams';
      params.viewId = this.$state.params.entityId;
      metadataApi = this.myTrackerApi.viewsProperties(params).$promise;
    } else {
      params.entityId = this.$state.params.entityId;
      params.entityType = entitySplit[0];
      metadataApi = this.myTrackerApi.properties(params).$promise;
    }

    this.systemTags = {};
    this.tags = {
      preferredTags: [],
      userTags: [],
      allPreferredTags: [],
      allUserTags: [],
      availableTags: []
    };
    this.fetchEntityTags();
    this.fetchAllTags();

    this.schema = [];
    this.properties = {};
    this.activePropertyTab = 0;

    this.loading = true;

    metadataApi.then( (res) => {
      this.loading = false;
      this.processResponse(res);
    }, (err) => {
      this.loading = false;
      console.log('Error', err);
    });

  }

  processResponse(res) {
    let systemMetadata, userMetadata;

    angular.forEach(res, (response) => {
      if (response.scope === 'USER') {
        userMetadata = response;
      } else if (response.scope === 'SYSTEM'){
        systemMetadata = response;
      }
    });

    this.systemTags = {
      system: systemMetadata.tags
    };

    this.properties = {
      system: systemMetadata.properties,
      user: userMetadata.properties,
      isUserEmpty: false,
      isSystemEmpty: false
    };

    /**
     * Need to show Dataset Spec from Dataset Properties if
     * dataset type is externalDataset. Ideally Backend should
     * return this automatically.
     **/
    if (systemMetadata.properties.type === 'externalDataset') {
      this.fetchExternalDatasetProperties();
    }

    if (Object.keys(userMetadata.properties).length === 0) {
      this.activePropertyTab = 1;
      this.properties.isUserEmpty = true;
    }

    this.properties.isSystemEmpty = Object.keys(systemMetadata.properties).length === 0;

    this.schema = systemMetadata.properties.schema;
  }

  fetchExternalDatasetProperties() {
    let datasetParams = {
      namespace: this.$state.params.namespace,
      entityId: this.$state.params.entityId,
      scope: this.$scope
    };
    this.myTrackerApi.getDatasetDetail(datasetParams)
      .$promise
      .then( (res) => {
        this.externalDatasetProperties = res.spec.properties;

        if (Object.keys(this.externalDatasetProperties).length > 0) {
          this.activePropertyTab = 0;
          this.properties.isUserEmpty = false;
        }
      });
  }

  /* METADATA PROPERTIES CONTROL */
  deleteProperty(key) {
    let deleteParams = {
      namespace: this.$state.params.namespace,
      entityType: this.$state.params.entityType,
      entityId: this.$state.params.entityId,
      key: key,
      scope: this.$scope
    };
    this.myTrackerApi.deleteEntityProperty(deleteParams)
      .$promise
      .then(() => {
        delete this.properties.user[key];
      }, (err) => {
        this.myAlertOnValium.show({
          type: 'danger',
          content: err.data
        });
      });
  }

  addProperty() {
    if (!this.propertyInput.key || !this.propertyInput.value) { return; }

    let addParams = {
      namespace: this.$state.params.namespace,
      entityType: this.$state.params.entityType,
      entityId: this.$state.params.entityId,
      scope: this.$scope
    };

    let obj = {};
    obj[this.propertyInput.key] = this.propertyInput.value;

    this.myTrackerApi.addEntityProperty(addParams, obj)
      .$promise
      .then(() => {
        this.properties.user[this.propertyInput.key] = this.propertyInput.value;

        this.propertyInput.key = '';
        this.propertyInput.value = '';
        this.propertyFocus();
      }, (err) => {
        this.myAlertOnValium.show({
          type: 'danger',
          content: err.data
        });
      });
  }

  propertyKeypress(event) {
    switch (event.keyCode) {
      case 13: // Enter Key
        this.addProperty();
        break;
    }
  }

  propertyFocus() {
    this.$timeout( () => {
      let elem = document.getElementById('property-key-input');
      angular.element(elem)[0].focus();
    });
  }

  /* TAGS CONTROL */

  fetchEntityTags() {
    let params = {
      namespace: this.$state.params.namespace,
      entityId: this.$state.params.entityId,
      entityType: this.$state.params.entityType === 'streams' ? 'stream' : 'dataset',
      scope: this.$scope
    };

    return this.myTrackerApi.getEntityTags(params)
      .$promise
      .then((response) => {
        const getTags = (tagsObj) => {
           return Object.keys(tagsObj)
            .map(tag => ({
              name: tag
            }));
        };
        this.tags.preferredTags = getTags(response.preferredTags);
        this.tags.userTags = getTags(response.userTags);

        this.updateAvailableTags();
      }, (err) => {
        console.log('Error', err);
      });
  }

  fetchAllTags() {
    let params = {
      namespace: this.$state.params.namespace,
      scope: this.$scope
    };

    this.myTrackerApi.getTags(params)
      .$promise
      .then((response) => {
        this.response = response;
        const getPreferredTags = (tagsObj) => {
           return Object.keys(tagsObj)
            .map(tag => ({
              name: tag,
              count: tagsObj[tag],
              label: tag + ' (' + tagsObj[tag] + ')',
              preferredTag: true
            }));
        };
        const getUserTags = (tagsObj) => {
           return Object.keys(tagsObj)
            .map(tag => ({
              name: tag,
              count: tagsObj[tag],
              label: tag
            }));
        };

        this.tags.allPreferredTags = getPreferredTags(response.preferredTags);
        this.tags.allUserTags = getUserTags(response.userTags);
        this.updateAvailableTags();
      }, (err) => {
        console.log('Error', err);
      });
  }

  updateAvailableTags() {
    const getFilteredTags = (allTags, addedTags) => {
      var addedTagNames = addedTags.map(tag => tag.name);
      return allTags.filter(tag => addedTagNames.indexOf(tag.name) === -1);
    };
    var filteredPrefferedTags = getFilteredTags(this.tags.allPreferredTags, this.tags.preferredTags);
    var filteredUserTags = getFilteredTags(this.tags.allUserTags, this.tags.userTags);
    this.tags.availableTags = filteredPrefferedTags.concat(filteredUserTags);
    return this.tags.availableTags;
  }

  addTag(input) {
    this.invalidFormat = false;
    this.duplicateTag = false;

    let defer = this.$q.defer();

    if (!input) {
      defer.reject();
      return defer.promise;
    }

    let userTagCheck = this.tags.userTags
              .filter(tag => input === tag.name).length > 0 ? true : false;
    let preferredTagCheck = this.tags.preferredTags
              .filter(tag => input === tag.name).length > 0 ? true : false;

    this.duplicateTag = userTagCheck || preferredTagCheck;

    if (!this.duplicateTag) {
      let addParams = {
        namespace: this.$state.params.namespace,
        entityId: this.$state.params.entityId,
        entityType: this.$state.params.entityType === 'streams' ? 'stream' : 'dataset',
        scope: this.$scope
      };

      this.myTrackerApi.addEntityTag(addParams, [input])
        .$promise
        .then(() => {
          this.fetchEntityTags()
            .then(this.updateAvailableTags.bind(this));

          defer.resolve('success');
        }, (err) => {
          if (err.statusCode === 500) {
            this.invalidFormat = true;
          }
          defer.reject('error');
        });
    } else {
      defer.reject('duplicate');
    }

    return defer.promise;
  }

  deleteTag(tag) {
    this.tag = tag;
    let deleteParams = {
      namespace: this.$state.params.namespace,
      tagname: tag,
      entityId: this.$state.params.entityId,
      entityType: this.$state.params.entityType === 'streams' ? 'stream' : 'dataset',
      scope: this.$scope
    };

    this.myTrackerApi.deleteEntityTag(deleteParams)
      .$promise
      .then(() => {
        this.fetchEntityTags();
      }, (err) => {
        this.myAlertOnValium.show({
          type: 'danger',
          content: err.data
        });
      });
  }

  goToTag(event, tag) {
    event.stopPropagation();
    this.$state.go('tracker.detail.result', {searchQuery: tag});
  }

  openTagInput(event) {
    event.stopPropagation();
    this.inputOpen = true;

    this.eventFunction = () => {
      this.escapeInput();
    };
    document.body.addEventListener('click', this.eventFunction, false);
  }

  escapeInput() {
    this.invalidFormat = false;
    this.duplicateTag = false;
    this.inputOpen = false;
    document.body.removeEventListener('click', this.eventFunction, false);
    this.eventFunction = null;
  }

}

TrackerMetadataController.$inject = ['$state', 'myTrackerApi', '$scope', 'myAlertOnValium', '$timeout', '$q'];

angular.module(PKG.name + '.feature.tracker')
  .controller('TrackerMetadataController', TrackerMetadataController);
