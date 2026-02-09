const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_encoding = require('../../common/encoding.js');
const require_common_onInit = require('../../common/onInit.js');
const require_v2_trace = require('../trace.js');
const require_common_providers_tasks = require('../../common/providers/tasks.js');
const require_v2_options = require('../options.js');

//#region src/v2/providers/tasks.ts
var tasks_exports = /* @__PURE__ */ require_rolldown_runtime.__export({ onTaskDispatched: () => onTaskDispatched });
function onTaskDispatched(optsOrHandler, handler) {
	let opts;
	if (arguments.length === 1) {
		opts = {};
		handler = optsOrHandler;
	} else {
		opts = optsOrHandler;
	}
	const fixedLen = (req) => handler(req);
	const func = require_v2_trace.wrapTraceContext(require_common_onInit.withInit(require_common_providers_tasks.onDispatchHandler(fixedLen)));
	Object.defineProperty(func, "__trigger", { get: () => {
		const baseOpts$1 = require_v2_options.optionsToTriggerAnnotations(require_v2_options.getGlobalOptions());
		const specificOpts$1 = require_v2_options.optionsToTriggerAnnotations(opts);
		const taskQueueTrigger = {};
		require_common_encoding.copyIfPresent(taskQueueTrigger, opts, "retryConfig", "rateLimits");
		require_common_encoding.convertIfPresent(taskQueueTrigger, require_v2_options.getGlobalOptions(), "invoker", "invoker", require_common_encoding.convertInvoker);
		require_common_encoding.convertIfPresent(taskQueueTrigger, opts, "invoker", "invoker", require_common_encoding.convertInvoker);
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
	const baseOpts = require_v2_options.optionsToEndpoint(require_v2_options.getGlobalOptions());
	const specificOpts = require_v2_options.optionsToEndpoint(opts);
	func.__endpoint = {
		platform: "gcfv2",
		...require_runtime_manifest.initV2Endpoint(require_v2_options.getGlobalOptions(), opts),
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		taskQueueTrigger: require_runtime_manifest.initTaskQueueTrigger(require_v2_options.getGlobalOptions(), opts)
	};
	require_common_encoding.copyIfPresent(func.__endpoint.taskQueueTrigger.retryConfig, opts.retryConfig, "maxAttempts", "maxBackoffSeconds", "maxDoublings", "maxRetrySeconds", "minBackoffSeconds");
	require_common_encoding.copyIfPresent(func.__endpoint.taskQueueTrigger.rateLimits, opts.rateLimits, "maxConcurrentDispatches", "maxDispatchesPerSecond");
	require_common_encoding.convertIfPresent(func.__endpoint.taskQueueTrigger, require_v2_options.getGlobalOptions(), "invoker", "invoker", require_common_encoding.convertInvoker);
	require_common_encoding.convertIfPresent(func.__endpoint.taskQueueTrigger, opts, "invoker", "invoker", require_common_encoding.convertInvoker);
	func.__requiredAPIs = [{
		api: "cloudtasks.googleapis.com",
		reason: "Needed for task queue functions"
	}];
	func.run = handler;
	return func;
}

//#endregion
exports.onTaskDispatched = onTaskDispatched;
Object.defineProperty(exports, 'tasks_exports', {
  enumerable: true,
  get: function () {
    return tasks_exports;
  }
});