import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { FormattedMessage, FormattedRelative, injectIntl, intlShape } from 'react-intl';

import EntityTitleInput from './EntityTitleInput';
import Header from '../common/Header';
import ContextMenu from '../common/ContextMenu';

require('./EntityTypeHeader.scss');

const IS_PRIMARY = true;

class EntityTypeHeader extends Component {

  constructor() {
    super();
    this.state = {
      titleEditModeActive: false,
    };
    this.handleToggleContextMenu = this.handleToggleContextMenu.bind(this);
  }

  handleToggleContextMenu(menuActive) {
    this.setState({ menuActive });
  }

  actionButtons(isPrimary = false) {
    const { actionButtons, intl } = this.props;

    if (actionButtons == null) return null;

    return (
      <ul>
        {actionButtons
          .filter(({ primary = false }) => (isPrimary && primary) || (!isPrimary && !primary))
          .map((button, index) =>
            <li key={index}>
              {button.subActions ? (
                <span>
                  <button
                    className={`overflow clickable ${button.customClass ? button.customClass : ''}`}
                    onClick={() => this.handleToggleContextMenu(index)}
                    title={button.tooltipId && intl.formatMessage({ id: button.tooltipId })}
                    disabled={button.disabled}
                    {...(button.props || {})}
                  >
                    {button.icon || null}
                    {button.buttonText}
                  </button>
                  {this.state.menuActive === index && (
                    <ContextMenu
                      options={button.subActions}
                      onOptionSelected={button.onOptionSelected}
                      onWindowClick={this.handleToggleContextMenu}
                    />
                  )}
                </span>
              ) : (
                <button
                  className={`overflow clickable ${button.customClass ? button.customClass : ''}`}
                  onClick={button.onClick}
                  title={button.tooltipId && intl.formatMessage({ id: button.tooltipId })}
                  disabled={button.disabled}
                  {...(button.props || {})}
                >
                  {button.icon || null}
                  {button.buttonText}
                </button>
              )}
            </li>
          )
        }
      </ul>
    );
  }

  render() {
    const {
      title,
      saveStatusId,
      onChangeTitle,
      onBeginEditTitle,
      timeToNextSave,
      savingFailed,
    } = this.props;

    return (
      <Header
        className="EntityTypeHeader"
        actions={this.actionButtons()}
        primaryActions={this.actionButtons(IS_PRIMARY)}
      >
        <EntityTitleInput
          title={title}
          onBeginEditTitle={onBeginEditTitle}
          onChangeTitle={onChangeTitle}
        />
        <div className="saveStatus">
          {saveStatusId && (
            <FormattedMessage id={saveStatusId} />
          )}
          {timeToNextSave && savingFailed && (
            <span>
              <FormattedRelative value={new Date().getTime() + timeToNextSave} />...
            </span>
          )}
        </div>
      </Header>
    );
  }
}

EntityTypeHeader.propTypes = {
  intl: intlShape.isRequired,
  title: PropTypes.string.isRequired,
  saveStatusId: PropTypes.string,
  actionButtons: PropTypes.array,
  onBeginEditTitle: PropTypes.func,
  onChangeTitle: PropTypes.func,
  timeToNextSave: PropTypes.number,
  savingFailed: PropTypes.bool,
};

export default injectIntl(EntityTypeHeader);
