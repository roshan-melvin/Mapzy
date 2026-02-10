const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_v1_cloud_functions = require('../cloud-functions.js');

//#region src/v1/providers/remoteConfig.ts
var remoteConfig_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	UpdateBuilder: () => UpdateBuilder,
	_onUpdateWithOptions: () => _onUpdateWithOptions,
	onUpdate: () => onUpdate,
	provider: () => provider,
	service: () => service
});
/** @internal */
const provider = "google.firebase.remoteconfig";
/** @internal */
const service = "firebaseremoteconfig.googleapis.com";
/**
* Registers a function that triggers on Firebase Remote Config template
* update events.
*
* @param handler A function that takes the updated Remote Config
*   template version metadata as an argument.
*
* @returns A function that you can export and deploy.
*/
function onUpdate(handler) {
	return _onUpdateWithOptions(handler, {});
}
/** @internal */
function _onUpdateWithOptions(handler, options) {
	const triggerResource = () => {
		if (!process.env.GCLOUD_PROJECT) {
			throw new Error("process.env.GCLOUD_PROJECT is not set.");
		}
		return `projects/${process.env.GCLOUD_PROJECT}`;
	};
	return new UpdateBuilder(triggerResource, options).onUpdate(handler);
}
/** Builder used to create Cloud Functions for Remote Config. */
var UpdateBuilder = class {
	/** @internal */
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
	}
	/**
	* Handle all updates (including rollbacks) that affect a Remote Config
	* project.
	* @param handler A function that takes the updated Remote Config template
	* version metadata as an argument.
	*/
	onUpdate(handler) {
		return require_v1_cloud_functions.makeCloudFunction({
			handler,
			provider,
			service,
			triggerResource: this.triggerResource,
			eventType: "update",
			options: this.options
		});
	}
};

//#endregion
exports.UpdateBuilder = UpdateBuilder;
exports._onUpdateWithOptions = _onUpdateWithOptions;
exports.onUpdate = onUpdate;
exports.provider = provider;
Object.defineProperty(exports, 'remoteConfig_exports', {
  enumerable: true,
  get: function () {
    return remoteConfig_exports;
  }
});
exports.service = service;