import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initV1Endpoint } from "../../runtime/manifest.mjs";
import { makeCloudFunction, optionsToEndpoint, optionsToTrigger } from "../cloud-functions.mjs";
import { HttpsError } from "../../common/providers/https.mjs";
import { UserRecordMetadata, userRecordConstructor, wrapHandler } from "../../common/providers/identity.mjs";

//#region src/v1/providers/auth.ts
var auth_exports = /* @__PURE__ */ __export({
	HttpsError: () => HttpsError,
	UserBuilder: () => UserBuilder,
	UserRecordMetadata: () => UserRecordMetadata,
	_userWithOptions: () => _userWithOptions,
	provider: () => provider,
	service: () => service,
	user: () => user,
	userRecordConstructor: () => userRecordConstructor
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
		return userRecordConstructor(raw.data);
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
		return makeCloudFunction({
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
		const func = wrapHandler(eventType, annotatedHandler);
		const legacyEventType = `providers/cloud.auth/eventTypes/user.${eventType}`;
		func.__trigger = {
			labels: {},
			...optionsToTrigger(this.options),
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
			...initV1Endpoint(this.options),
			...optionsToEndpoint(this.options),
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
export { HttpsError, UserBuilder, UserRecordMetadata, _userWithOptions, auth_exports, provider, service, user, userRecordConstructor };