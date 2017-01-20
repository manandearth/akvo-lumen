(ns akvo.lumen.component.keycloak
  "Keycloak component."
  (:require [cheshire.core :as json]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [ring.util.response :refer [not-found response]]))


;;; The "agent user account" needs to have the client role role mapping:
;;; manage-realm
;;; manage-users
;;;

(defprotocol UserManager
  (users [this tenant-label] "List tenants users")
  (add-user [this tenant-label user] "Add user to tenant"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Helpers
;;;

(defn fetch-openid-configuration
  "Get the openid configuration"
  [issuer]
  (-> (client/get (format "%s/.well-known/openid-configuration" issuer))
      :body json/decode))

(defn fetch-access-token [openid-config credentials]
  (let [params (merge {"grant_type" "password"} credentials)
        response (client/post (get openid-config "token_endpoint")
                              {:form-params params})]
    (-> response :body json/decode (get "access_token"))))

(defn headers-with-token [openid-config credentials]
  {"Authorization" (str "bearer " (fetch-access-token openid-config credentials))
   "Content-Type" "application/json"})

(defn get-users [{:keys [api-root credentials openid-config]} tenant-label]
  (println "@keycloak/get-users")
  (let [url (format "%s/users" api-root)
        resp (-> (client/get url {:headers
                                  (headers-with-token openid-config
                                                      credentials)})
                 :body json/decode)
        enabled-verified-users (filter (fn [user]
                                         (and (get user "enabled")
                                              (get user "emailVerified"))) resp)
        trimmed-users (map (fn [user]
                             (select-keys user ["username" "id" "email"]))
                           enabled-verified-users)]
    (println "---------------------------------------------------------------")
    (clojure.pprint/pprint resp)
    #_(clojure.pprint/pprint trimmed-users)
    (println "---------------------------------------------------------------")
    (response trimmed-users)))


(defn get-users-by-group-path
  ""
  [{:keys [api-root credentials openid-config]} tenant-label]
  (println "@keycloak/get-users-by-group-path")
  (println api-root)
  (println tenant-label)
  (let [url (format "%s/group-by-path/%s/" api-root tenant-label)
        resp (-> (client/get url {:headers
                                  (headers-with-token openid-config
                                                      credentials)})
                 :body json/decode)
        ]
    (println "---------------------------------------------------------------")
    #_(clojure.pprint/pprint resp)
    #_(clojure.pprint/pprint trimmed-users)
    (println url)
    (println "---------------------------------------------------------------")
    (response resp)))

;; tenant-label -> path -> t1
;; tenant-group-path t1/
;; tenant-admin-group t1/admin


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Component
;;;

(defrecord KeycloakAgent [issuer openid-config api-root]

  component/Lifecycle
  (start [this]
    (assoc this :openid-config (fetch-openid-configuration issuer)))

  (stop [this]
    (assoc this :openid-config nil))

  UserManager
  (users [this tenant-label]
    (println "@keycloak/users")
    (get-users-by-group-path this tenant-label))

  (add-user [this tenant-label user]
    (clojure.pprint/pprint this)
    (println "Add user")
    "ok"))

(defn keycloak [{:keys [url realm user password]}]
  (map->KeycloakAgent {:issuer (format "%s/realms/%s" url realm)
                       :api-root (format "%s/admin/realms/%s" url realm)
                       :credentials {"username" user
                                     "password" password
                                     "client_id" "akvo-lumen"
                                     }}))
