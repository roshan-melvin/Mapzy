import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initTaskQueueTrigger, initV1Endpoint } from "../../runtime/manifest.mjs";
import { convertIfPresent, convertInvoker, copyIfPresent } from "../../common/encoding.mjs";
import { optionsToEndpoint, optionsToTrigger } from "../cloud-functions.mjs";
import { onDispatchHandler } from "../../common/providers/tasks.mjs";

//#region src/v1/providers/tasks.ts
var tasks_exports = /* @__PURE__ */ __export({
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
		const func = onDispatchHandler(fixedLen);
		func.__trigger = {
			...optionsToTrigger(this.depOpts || {}),
			taskQueueTrigger: {}
		};
		copyIfPresent(func.__trigger.taskQueueTrigger, this.tqOpts, "retryConfig");
		copyIfPresent(func.__trigger.taskQueueTrigger, this.tqOpts, "rateLimits");
		convertIfPresent(func.__trigger.taskQueueTrigger, this.tqOpts, "invoker", "invoker", convertInvoker);
		func.__endpoint = {
			platform: "gcfv1",
			...initV1Endpoint(this.depOpts),
			...optionsToEndpoint(this.depOpts),
			taskQueueTrigger: initTaskQueueTrigger(this.depOpts)
		};
		copyIfPresent(func.__endpoint.taskQueueTrigger.retryConfig, this.tqOpts?.retryConfig || {}, "maxAttempts", "maxBackoffSeconds", "maxDoublings", "maxRetrySeconds", "minBackoffSeconds");
		copyIfPresent(func.__endpoint.taskQueueTrigger.rateLimits, this.tqOpts?.rateLimits || {}, "maxConcurrentDispatches", "maxDispatchesPerSecond");
		convertIfPresent(func.__endpoint.taskQueueTrigger, this.tqOpts, "invoker", "invoker", convertInvoker);
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
export { TaskQueueBuilder, taskQueue, tasks_exports };