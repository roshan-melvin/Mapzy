const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_encoding = require('../../common/encoding.js');
const require_v1_cloud_functions = require('../cloud-functions.js');
const require_common_providers_tasks = require('../../common/providers/tasks.js');

//#region src/v1/providers/tasks.ts
var tasks_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	TaskQueueBuilder: () => TaskQueueBuilder,
	taskQueue: () => taskQueue
});
/**
* Builder for creating a `TaskQueueFunction`.
*/
var TaskQueueBuilder = class {
	/** @internal */
	constructor(tqOpts, depOpts) {
		this.tqOpts = tqOpts;
		this.depOpts = depOpts;
	}
	/**
	* Creates a handler for tasks sent to a Google Cloud Tasks queue.
	* @param handler - A callback to handle task requests.
	* @returns A function you can export and deploy.
	*/
	onDispatch(handler) {
		const fixedLen = (data, context) => handler(data, context);
		const func = require_common_providers_tasks.onDispatchHandler(fixedLen);
		func.__trigger = {
			...require_v1_cloud_functions.optionsToTrigger(this.depOpts || {}),
			taskQueueTrigger: {}
		};
		require_common_encoding.copyIfPresent(func.__trigger.taskQueueTrigger, this.tqOpts, "retryConfig");
		require_common_encoding.copyIfPresent(func.__trigger.taskQueueTrigger, this.tqOpts, "rateLimits");
		require_common_encoding.convertIfPresent(func.__trigger.taskQueueTrigger, this.tqOpts, "invoker", "invoker", require_common_encoding.convertInvoker);
		func.__endpoint = {
			platform: "gcfv1",
			...require_runtime_manifest.initV1Endpoint(this.depOpts),
			...require_v1_cloud_functions.optionsToEndpoint(this.depOpts),
			taskQueueTrigger: require_runtime_manifest.initTaskQueueTrigger(this.depOpts)
		};
		require_common_encoding.copyIfPresent(func.__endpoint.taskQueueTrigger.retryConfig, this.tqOpts?.retryConfig || {}, "maxAttempts", "maxBackoffSeconds", "maxDoublings", "maxRetrySeconds", "minBackoffSeconds");
		require_common_encoding.copyIfPresent(func.__endpoint.taskQueueTrigger.rateLimits, this.tqOpts?.rateLimits || {}, "maxConcurrentDispatches", "maxDispatchesPerSecond");
		require_common_encoding.convertIfPresent(func.__endpoint.taskQueueTrigger, this.tqOpts, "invoker", "invoker", require_common_encoding.convertInvoker);
		func.__requiredAPIs = [{
			api: "cloudtasks.googleapis.com",
			reason: "Needed for task queue functions"
		}];
		func.run = handler;
		return func;
	}
};
/**
* Declares a function that can handle tasks enqueued using the Firebase Admin SDK.
* @param options - Configuration for the Task Queue that feeds into this function.
*        Omitting options will configure a Task Queue with default settings.
*/
function taskQueue(options) {
	return new TaskQueueBuilder(options);
}

//#endregion
exports.TaskQueueBuilder = TaskQueueBuilder;
exports.taskQueue = taskQueue;
Object.defineProperty(exports, 'tasks_exports', {
  enumerable: true,
  get: function () {
    return tasks_exports;
  }
});