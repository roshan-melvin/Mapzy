import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initV2Endpoint } from "../../runtime/manifest.mjs";
import { withInit } from "../../common/onInit.mjs";
import { HttpsError } from "../../common/providers/https.mjs";
import { wrapHandler } from "../../common/providers/identity.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { getGlobalOptions, optionsToEndpoint } from "../options.mjs";

//#region src/v2/providers/identity.ts
var identity_exports = /* @__PURE__ */ __export({
	HttpsError: () => HttpsError,
	beforeEmailSent: () => beforeEmailSent,
	beforeOperation: () => beforeOperation,
	beforeSmsSent: () => beforeSmsSent,
	beforeUserCreated: () => beforeUserCreated,
	beforeUserSignedIn: () => beforeUserSignedIn,
	getOpts: () => getOpts
});
/**
* Handles an event that is triggered before a user is created.
* @param optsOrHandler - Either an object containing function options, or an event handler (run before user creation).
* @param handler? - If defined, an event handler which is run every time before a user is created.
*/
function beforeUserCreated(optsOrHandler, handler) {
	return beforeOperation("beforeCreate", optsOrHandler, handler);
}
/**
* Handles an event that is triggered before a user is signed in.
* @param optsOrHandler - Either an object containing function options, or an event handler (run before user signin).
* @param handler - Event handler which is run every time before a user is signed in.
*/
function beforeUserSignedIn(optsOrHandler, handler) {
	return beforeOperation("beforeSignIn", optsOrHandler, handler);
}
/**
* Handles an event that is triggered before an email is sent to a user.
* @param optsOrHandler- Either an object containing function options, or an event handler that is run before an email is sent to a user.
* @param handler - Event handler that is run before an email is sent to a user.
*/
function beforeEmailSent(optsOrHandler, handler) {
	return beforeOperation("beforeSendEmail", optsOrHandler, handler);
}
/**
* Handles an event that is triggered before an SMS is sent to a user.
* @param optsOrHandler - Either an object containing function options, or an event handler that is run before an SMS is sent to a user.
* @param handler - Event handler that is run before an SMS is sent to a user.
*/
function beforeSmsSent(optsOrHandler, handler) {
	return beforeOperation("beforeSendSms", optsOrHandler, handler);
}
/** @hidden */
function beforeOperation(eventType, optsOrHandler, handler) {
	if (!handler || typeof optsOrHandler === "function") {
		handler = optsOrHandler;
		optsOrHandler = {};
	}
	const { opts,...blockingOptions } = getOpts(optsOrHandler);
	const annotatedHandler = Object.assign(handler, { platform: "gcfv2" });
	const func = wrapTraceContext(withInit(wrapHandler(eventType, annotatedHandler)));
	const legacyEventType = `providers/cloud.auth/eventTypes/user.${eventType}`;
	/** Endpoint */
	const baseOptsEndpoint = optionsToEndpoint(getGlobalOptions());
	const specificOptsEndpoint = optionsToEndpoint(opts);
	func.__endpoint = {
		...initV2Endpoint(getGlobalOptions(), opts),
		platform: "gcfv2",
		...baseOptsEndpoint,
		...specificOptsEndpoint,
		labels: {
			...baseOptsEndpoint?.labels,
			...specificOptsEndpoint?.labels
		},
		blockingTrigger: {
			eventType: legacyEventType,
			options: { ...(eventType === "beforeCreate" || eventType === "beforeSignIn") && blockingOptions }
		}
	};
	func.__requiredAPIs = [{
		api: "identitytoolkit.googleapis.com",
		reason: "Needed for auth blocking functions"
	}];
	func.run = handler;
	return func;
}
/** @hidden */
function getOpts(blockingOptions) {
	const accessToken = blockingOptions.accessToken || false;
	const idToken = blockingOptions.idToken || false;
	const refreshToken = blockingOptions.refreshToken || false;
	const opts = { ...blockingOptions };
	delete opts.accessToken;
	delete opts.idToken;
	delete opts.refreshToken;
	return {
		opts,
		accessToken,
		idToken,
		refreshToken
	};
}

//#endregion
export { HttpsError, beforeEmailSent, beforeOperation, beforeSmsSent, beforeUserCreated, beforeUserSignedIn, getOpts, identity_exports };