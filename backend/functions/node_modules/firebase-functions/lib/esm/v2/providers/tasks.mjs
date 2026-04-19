import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initTaskQueueTrigger, initV2Endpoint } from "../../runtime/manifest.mjs";
import { convertIfPresent, convertInvoker, copyIfPresent } from "../../common/encoding.mjs";
import { withInit } from "../../common/onInit.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { onDispatchHandler } from "../../common/providers/tasks.mjs";
import { getGlobalOptions, optionsToEndpoint, optionsToTriggerAnnotations } from "../options.mjs";

//#region src/v2/providers/tasks.ts
var tasks_exports = /* @__PURE__ */ __export({ onTaskDispatched: () => onTaskDispatched });
function onTaskDispatched(optsOrHandler, handler) {
	let opts;
	if (arguments.length === 1) {
		opts = {};
		handler = optsOrHandler;
	} else {
		opts = optsOrHandler;
	}
	const fixedLen = (req) => handler(req);
	const func = wrapTraceContext(withInit(onDispatchHandler(fixedLen)));
	Object.defineProperty(func, "__trigger", { get: () => {
		const baseOpts$1 = optionsToTriggerAnnotations(getGlobalOptions());
		const specificOpts$1 = optionsToTriggerAnnotations(opts);
		const taskQueueTrigger = {};
		copyIfPresent(taskQueueTrigger, opts, "retryConfig", "rateLimits");
		convertIfPresent(taskQueueTrigger, getGlobalOptions(), "invoker", "invoker", convertInvoker);
		convertIfPresent(taskQueueTrigger, opts, "invoker", "invoker", convertInvoker);
		return {
			platform: "gcfv2",
			...baseOpts$1,
			...specificOpts$1,
			labels: {
				...baseOpts$1?.labels,
				...specificOpts$1?.labels
			},
			taskQueueTrigger
		};
	} });
	const baseOpts = optionsToEndpoint(getGlobalOptions());
	const specificOpts = optionsToEndpoint(opts);
	func.__endpoint = {
		platform: "gcfv2",
		...initV2Endpoint(getGlobalOptions(), opts),
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		taskQueueTrigger: initTaskQueueTrigger(getGlobalOptions(), opts)
	};
	copyIfPresent(func.__endpoint.taskQueueTrigger.retryConfig, opts.retryConfig, "maxAttempts", "maxBackoffSeconds", "maxDoublings", "maxRetrySeconds", "minBackoffSeconds");
	copyIfPresent(func.__endpoint.taskQueueTrigger.rateLimits, opts.rateLimits, "maxConcurrentDispatches", "maxDispatchesPerSecond");
	convertIfPresent(func.__endpoint.taskQueueTrigger, getGlobalOptions(), "invoker", "invoker", convertInvoker);
	convertIfPresent(func.__endpoint.taskQueueTrigger, opts, "invoker", "invoker", convertInvoker);
	func.__requiredAPIs = [{
		api: "cloudtasks.googleapis.com",
		reason: "Needed for task queue functions"
	}];
	func.run = handler;
	return func;
}

//#endregion
export { onTaskDispatched, tasks_exports };