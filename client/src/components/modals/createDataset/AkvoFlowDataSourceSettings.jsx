import React, { Component, PropTypes } from 'react';
import Select from 'react-select';
import { connect } from 'react-redux';
import * as api from '../../../api';
import * as keycloak from '../../../auth';

const acceptHeader = { Accept: 'application/vnd.akvo.flow.v2+json' };

function rootFoldersUrl(flowApiRoot, flowInstance) {
  return `${flowApiRoot}/orgs/${flowInstance}/folders`;
}

function merge(a, b) {
  return Object.assign({}, a, b);
}

function indexById(objects) {
  return objects.reduce((index, obj) => merge(index, { [obj.id]: obj }), {});
}

function parseInstance(text) {
  if (text == null || text.trim() === '') return null;
  const match = text.trim().toLowerCase().match(/^(https?:\/\/)?([a-z0-9_-]+)\.?.*$/);
  if (match != null) {
    return match[2];
  }
  return null;
}

class AkvoFlowDataSourceSettings extends Component {
  static isValidSource({ instance, surveyId, formId }) {
    return typeof instance === 'string'
      && typeof surveyId === 'string'
      && typeof formId === 'string';
  }

  constructor(props) {
    super(props);
    this.state = {
      errorMessage: null,
      instance: null,
      // selected folder ids,
      selectedFolders: [],
      selectedSurveyId: null,
      selectedFormId: null,
      // store of all fetched folders, surveys and survey definitions
      folders: {},
      surveys: {},
      surveyDefinitions: {},
    };
  }

  resetSelections() {
    this.setState({
      errorMessage: null,
      instance: null,
      selectedFolders: [],
      selectedSurveyId: null,
      selectedFormId: null,
      folders: {},
      surveys: {},
      surveyDefinitions: {},
    });
  }

  handleFlowInstanceChange(text) {
    const { flowApiRoot } = this.props;
    const delay = 900; // ms
    clearTimeout(this.flowInstanceChangeTimeout);
    this.resetSelections();
    this.flowInstanceChangeTimeout = setTimeout(
      () => {
        const instance = parseInstance(text);
        if (instance == null) return;
        api.get(rootFoldersUrl(flowApiRoot, instance), { parentId: 0 }, acceptHeader)
          .then((response) => {
            if (response.ok) {
              return response.json();
            } else if (response.status === 404) {
              throw new Error(`No such flow instance: ${instance}`);
            } else if (response.status === 403) {
              throw new Error('Not authorized');
            }
            throw new Error(`Unexpected response ${response.status}`);
          })
          .then(folders => this.setState({
            instance,
            folders: merge(this.state.folders, indexById(folders)),
          }))
          .catch(err => this.setState({ errorMessage: err.message }));
      },
      delay
    );
  }

  folderSelectionOptions(parentId) {
    return Object
      .values(this.state.folders)
      .filter(folder => folder.parentId === parentId)
      .map(folder => ({
        label: folder.name,
        value: folder.id,
      }));
  }

  surveySelectionOptions(folderId) {
    return Object
     .values(this.state.surveys)
     .filter(survey => survey.folderId === folderId)
     .map(survey => ({
       label: survey.name,
       value: survey.id,
     }));
  }

  formSelectionOptions(surveyId) {
    const surveyDefinition = this.state.surveyDefinitions[surveyId];
    if (surveyDefinition != null) {
      return surveyDefinition.forms.map(form => ({
        label: form.name,
        value: form.id,
      }));
    }
    return [];
  }

  foldersAndSurveysSelectionOptions(parentId) {
    return this.folderSelectionOptions(parentId).concat(this.surveySelectionOptions(parentId));
  }

  handleFolderSelection(selectedFolderId) {
    const { selectedFolders } = this.state;
    const selectedFolder = this.state.folders[selectedFolderId];
    this.setState({ selectedFolders: selectedFolders.concat([selectedFolderId]) });
    Promise.all([
      api.get(selectedFolder.foldersUrl, null, acceptHeader).then(response => response.json()),
      api.get(selectedFolder.surveysUrl, null, acceptHeader).then(response => response.json()),
    ]).then(([folders, surveys]) => this.setState({
      surveys: merge(this.state.surveys, indexById(surveys)),
      folders: merge(this.state.folders, indexById(folders)),
    }));
  }

  handleSurveySelection(selectedSurveyId) {
    const { surveys, surveyDefinitions } = this.state;
    this.setState({ selectedSurveyId });
    const surveyUrl = surveys[selectedSurveyId].survey;
    api.get(surveyUrl, null, acceptHeader)
      .then(response => response.json())
      .then(surveyDefinition => this.setState({
        surveyDefinitions: merge(surveyDefinitions, {
          [surveyDefinition.id]: surveyDefinition,
        }),
      }));
  }

  handleFormSelection(selectedFormId) {
    const { surveyDefinitions, selectedSurveyId, instance } = this.state;
    const form = surveyDefinitions[selectedSurveyId].forms.find(f => f.id === selectedFormId);
    this.setState({ selectedFormId });
    this.props.onChange({
      kind: 'AKVO_FLOW',
      instance,
      surveyId: selectedSurveyId,
      formId: selectedFormId,
      refreshToken: keycloak.refreshToken(),
    });
    this.props.onChangeSettings({ name: form.name });
  }

  handleSelection(evt) {
    const selectedId = evt.value;
    const { folders } = this.state;

    if (folders[selectedId] != null) {
      this.handleFolderSelection(selectedId);
    } else {
      this.handleSurveySelection(selectedId);
    }
  }

  changeFolderSelection(evt, idx) {
    this.setState({
      selectedFolders: this.state.selectedFolders.slice(0, idx),
      selectedSurveyId: null,
      selectedFormId: null,
    }, () => this.handleSelection(evt));
  }

  render() {
    const {
      errorMessage,
      instance,
      selectedFolders,
      selectedSurveyId,
      selectedFormId,
      folders,
    } = this.state;

    const errorNotification = errorMessage != null && (
      <div>
        <span>{errorMessage}</span>
      </div>
    );

    const folderSelections = selectedFolders.map((id, idx) => {
      const selectedFolder = folders[id];
      const parentId = selectedFolder.parentId;
      const options = this.foldersAndSurveysSelectionOptions(parentId);
      return (
        <Select
          clearable={false}
          options={options}
          value={id}
          onChange={evt => this.changeFolderSelection(evt, idx)}
        />
      );
    });

    const lastSelectedFolderId = selectedFolders.length === 0 ?
      '0' : selectedFolders[selectedFolders.length - 1];

    // Either a survey or a folder can be selected
    const nextSelection = instance != null && (
      <Select
        clearable={false}
        options={this.foldersAndSurveysSelectionOptions(lastSelectedFolderId)}
        value={selectedSurveyId}
        onChange={evt => this.handleSelection(evt)}
      />
    );

    const formSelection = selectedSurveyId != null && (
      <Select
        clearable={false}
        options={this.formSelectionOptions(selectedSurveyId)}
        value={selectedFormId}
        onChange={evt => this.handleFormSelection(evt.value)}
      />
    );

    return (
      <div>
        {errorNotification}
        <input
          placeholder="Flow Application URL"
          onChange={evt => this.handleFlowInstanceChange(evt.target.value)}
          type="text"
          style={{ width: '300px' }}
        />
        {folderSelections}
        {nextSelection}
        {formSelection}
      </div>
    );
  }
}

export default connect(state => ({
  flowApiRoot: state.env.flowApiRoot,
}))(AkvoFlowDataSourceSettings);

AkvoFlowDataSourceSettings.propTypes = {
  flowApiRoot: PropTypes.string.isRequired,
  dataSource: PropTypes.shape({
    instance: PropTypes.string,
    surveyId: PropTypes.string,
    formId: PropTypes.string,
  }).isRequired,
  onChange: PropTypes.func.isRequired,
  onChangeSettings: PropTypes.func.isRequired,
};
