/* eslint-disable no-underscore-dangle */
import fetch from 'isomorphic-fetch';
import Keycloak from 'keycloak-js';
import auth0 from 'auth0-js';
import Raven from 'raven-js';
import queryString from 'query-string';

let keycloak = null;
let accessToken = null;

export function token() {
  if (!keycloak) {
    return Promise.resolve(accessToken);
  }

  return new Promise(resolve =>
    keycloak
      .updateToken()
      .success(() => resolve(keycloak.token))
      .error(() => {
        // Redirect to login page
        keycloak.login();
      })
  );
}

export function refreshToken() {
  if (keycloak == null) {
    throw new Error('Keycloak not initialized');
  }
  return keycloak.refreshToken;
}

let aauth0 = new auth0.WebAuth({
  domain: 'dantestakvo.eu.auth0.com',
  clientID: 'DhFdUkPQPwosdWatLsTYO3e85Tn9z7RE',
  redirectUri: 'http://t1.lumen.local:3030/library',
  audience: "https://foo.akvo.org",
  responseType: 'token id_token',
  scope: 'openid'
});

export function init() {
    return aauth0.authorize();
}

export function initPublic() {
  return fetch('/env')
    .then(response => response.json())
    .then(env => ({ env }));
}

function handleAuthentication() {
    aauth0.parseHash((err, authResult) => {
      if (authResult && authResult.accessToken && authResult.idToken) {
        console.log("All good");
      } else if (err) {
        console.log(err);
        alert(`Error: ${err.error}. Check the console for further details.`);
      }
    });
  }


export function initExport(providedAccessToken) {
  handleAuthentication();
  return Promise.resolve((accessToken = providedAccessToken));
}

