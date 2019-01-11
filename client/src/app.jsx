/* eslint-disable import/default, global-require, import/first, no-underscore-dangle */
import React from 'react';
import { render } from 'react-dom';
import { browserHistory } from 'react-router';
import { AppContainer } from 'react-hot-loader';
import { syncHistoryWithStore } from 'react-router-redux';
import Root from './containers/Root';
import configureStore from './store/configureStore';
import * as auth from './auth';
import { init as initAnalytics } from './utilities/analytics';
import queryString from 'query-string';

function initAuthenticated(profile, env) {
  const initialState = { profile, env };

  const store = configureStore(initialState);
  const history = syncHistoryWithStore(browserHistory, store);
  const rootElement = document.querySelector('#root');

  store.subscribe(() => {
    initAnalytics(store.getState());
  });

  // Refreshing the token on a fixed schedule (every 10 minutes)
  // will disable SSO Idle Timeout
  setInterval(auth.token, 1000 * 60 * 10);

  render(
    <AppContainer>
      <Root store={store} history={history} />
    </AppContainer>,
    rootElement
  );

  if (module.hot) {
    module.hot.accept('./containers/Root', () => {
      const NewRoot = require('./containers/Root').default;
      render(
        <AppContainer>
          <NewRoot store={store} history={history} />
        </AppContainer>,
        rootElement
      );
    });
  }
}

function initWithAuthToken(locale) {
  const initialState = { profile: { attributes: { locale: [locale] } } };
  const rootElement = document.querySelector('#root');
  const store = configureStore(initialState);
  const history = syncHistoryWithStore(browserHistory, store);

  render(
    <AppContainer>
      <Root store={store} history={history} />
    </AppContainer>,
    rootElement
  );
}

function initNotAuthenticated(msg) {
  document.querySelector('#root').innerHTML = msg;
}

function dispatchOnMode() {
  const queryParams = queryString.parse(location.hash);
  const accessToken = queryParams.access_token;
  if (accessToken == null) {
    auth
      .init()
      .then(({ profile, env }) => initAuthenticated(profile, env))
      .catch(err => initNotAuthenticated(err.message));
  } else {
    auth.initExport(accessToken).then(initWithAuthToken(queryParams.locale));
  }
}

dispatchOnMode();
