const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_v1_cloud_functions = require('../cloud-functions.js');
const require_common_providers_https = require('../../common/providers/https.js');
const require_common_providers_identity = require('../../common/providers/identity.js');

//#region src/v1/providers/auth.ts
var auth_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	HttpsError: () => require_common_providers_https.HttpsError,
	UserBuilder: () => UserBuilder,
	UserRecordMetadata: () => require_common_providers_identity.UserRecordMetadata,
	_userWithOptions: () => _userWithOptions,
	provider: () => provider,
	service: () => service,
	user: () => user,
	userRecordConstructor: () => require_common_providers_identity.userRecordConstructor
});
/** @internal */
const provider = "google.firebase.auth";
/** @internal */
const service = "firebaseauth.googleapis.com";
/**
* Handles events related to Firebase Auth users events.
*
* @param userOptions - Resource level options
* @returns UserBuilder - Builder used to create functions for Firebase Auth user lifecycle events
*
* @public
*/
function user(userOptions) {
	return _userWithOptions({}, userOptions || {});
}
/** @internal */
function _userWithOptions(options, userOptions) {
	return new UserBuilder(() => {
		if (!process.env.GCLOUD_PROJECT) {
			throw new Error("process.env.GCLOUD_PROJECT is not set.");
		}
		return "projects/" + process.env.GCLOUD_PROJECT;
	}, options, userOptions);
}
/**
* Builder used to create functions for Firebase Auth user lifecycle events.
* @public
*/
var UserBuilder = class UserBuilder {
	static dataConstructor(raw) {
		return require_common_providers_identity.userRecordConstructor(raw.data);
	}
	constructor(triggerResource, options, userOptions) {
		this.triggerResource = triggerResource;
		this.options = options;
		this.userOptions = userOptions;
	}
	/**
	* Responds to the creation of a Firebase Auth user.
	*
	* @param handler Event handler that responds to the creation of a Firebase Auth user.
	*
	* @public
	*/
	onCreate(handler) {
		return this.onOperation(handler, "user.create");
	}
	/**
	* Responds to the deletion of a Firebase Auth user.
	*
	* @param handler Event handler that responds to the deletion of a Firebase Auth user.
	*
	* @public
	*/
	onDelete(handler) {
		return this.onOperation(handler, "user.delete");
	}
	/**
	* Blocks request to create a Firebase Auth user.
	*
	* @param handler Event handler that blocks creation of a Firebase Auth user.
	*
	* @public
	*/
	beforeCreate(handler) {
		return this.beforeOperation(handler, "beforeCreate");
	}
	/**
	* Blocks request to sign-in a Firebase Auth user.
	*
	* @param handler Event handler that blocks sign-in of a Firebase Auth user.
	*
	* @public
	*/
	beforeSignIn(handler) {
		return this.beforeOperation(handler, "beforeSignIn");
	}
	beforeEmail(handler) {
		return this.beforeOperation(handler, "beforeSendEmail");
	}
	beforeSms(handler) {
		return this.beforeOperation(handler, "beforeSendSms");
	}
	onOperation(handler, eventType) {
		return require_v1_cloud_functions.makeCloudFunction({
			handler,
			provider,
			eventType,
			service,
			triggerResource: this.triggerResource,
			dataConstructor: UserBuilder.dataConstructor,
			legacyEventType: `providers/firebase.auth/eventTypes/${eventType}`,
			options: this.options
		});
	}
	beforeOperation(handler, eventType) {
		const accessToken = this.userOptions?.blockingOptions?.accessToken || false;
		const idToken = this.userOptions?.blockingOptions?.idToken || false;
		const refreshToken = this.userOptions?.blockingOptions?.refreshToken || false;
		const annotatedHandler = Object.assign(handler, { platform: "gcfv1" });
		const func = require_common_providers_identity.wrapHandler(eventType, annotatedHandler);
		const legacyEventType = `providers/cloud.auth/eventTypes/user.${eventType}`;
		func.__trigger = {
			labels: {},
			...require_v1_cloud_functions.optionsToTrigger(this.options),
			blockingTrigger: {
				eventType: legacyEventType,
				options: {
					accessToken,
					idToken,
					refreshToken
				}
			}
		};
		func.__endpoint = {
			platform: "gcfv1",
			labels: {},
			...require_runtime_manifest.initV1Endpoint(this.options),
			...require_v1_cloud_functions.optionsToEndpoint(this.options),
			blockingTrigger: {
				eventType: legacyEventType,
				options: {
					accessToken,
					idToken,
					refreshToken
				}
			}
		};
		func.__requiredAPIs = [{
			api: "identitytoolkit.googleapis.com",
			reason: "Needed for auth blocking functions"
		}];
		func.run = handler;
		return func;
	}
};

//#endregion
exports.HttpsError = require_common_providers_https.HttpsError;
exports.UserBuilder = UserBuilder;
exports.UserRecordMetadata = require_common_providers_identity.UserRecordMetadata;
exports._userWithOptions = _userWithOptions;
Object.defineProperty(exports, 'auth_exports', {
  enumerable: true,
  get: function () {
    return auth_exports;
  }
});
exports.provider = provider;
exports.service = service;
exports.user = user;
exports.userRecordConstructor = require_common_providers_identity.userRecordConstructor;