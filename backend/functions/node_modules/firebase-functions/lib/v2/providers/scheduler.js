const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_logger_index = require('../../logger/index.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_encoding = require('../../common/encoding.js');
const require_common_onInit = require('../../common/onInit.js');
const require_v2_trace = require('../trace.js');
const require_v2_options = require('../options.js');

//#region src/v2/providers/scheduler.ts
var scheduler_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	getOpts: () => getOpts,
	onSchedule: () => onSchedule
});
/** @internal */
function getOpts(args) {
	if (typeof args === "string") {
		return {
			schedule: args,
			opts: {}
		};
	}
	return {
		schedule: args.schedule,
		timeZone: args.timeZone,
		retryConfig: {
			retryCount: args.retryCount,
			maxRetrySeconds: args.maxRetrySeconds,
			minBackoffSeconds: args.minBackoffSeconds,
			maxBackoffSeconds: args.maxBackoffSeconds,
			maxDoublings: args.maxDoublings
		},
		opts: args
	};
}
/**
* Handler for scheduled functions. Triggered whenever the associated
* scheduler job sends a http request.
* @param args - Either a schedule or an object containing function options.
* @param handler - A function to execute when triggered.
* @returns A function that you can export and deploy.
*/
function onSchedule(args, handler) {
	const separatedOpts = getOpts(args);
	const httpFunc = async (req, res) => {
		const event = {
			jobName: req.header("X-CloudScheduler-JobName") || undefined,
			scheduleTime: req.header("X-CloudScheduler-ScheduleTime") || new Date().toISOString()
		};
		try {
			await handler(event);
			res.status(200).send();
		} catch (err) {
			require_logger_index.error(err.message);
			res.status(500).send();
		}
	};
	const func = require_v2_trace.wrapTraceContext(require_common_onInit.withInit(httpFunc));
	func.run = handler;
	const globalOpts = require_v2_options.getGlobalOptions();
	const baseOptsEndpoint = require_v2_options.optionsToEndpoint(globalOpts);
	const specificOptsEndpoint = require_v2_options.optionsToEndpoint(separatedOpts.opts);
	const ep = {
		...require_runtime_manifest.initV2Endpoint(globalOpts, separatedOpts.opts),
		platform: "gcfv2",
		...baseOptsEndpoint,
		...specificOptsEndpoint,
		labels: {
			...baseOptsEndpoint?.labels,
			...specificOptsEndpoint?.labels
		},
		scheduleTrigger: require_runtime_manifest.initV2ScheduleTrigger(separatedOpts.schedule, globalOpts, separatedOpts.opts)
	};
	require_common_encoding.copyIfPresent(ep.scheduleTrigger, separatedOpts, "timeZone");
	require_common_encoding.copyIfPresent(ep.scheduleTrigger.retryConfig, separatedOpts.retryConfig, "retryCount", "maxRetrySeconds", "minBackoffSeconds", "maxBackoffSeconds", "maxDoublings");
	func.__endpoint = ep;
	func.__requiredAPIs = [{
		api: "cloudscheduler.googleapis.com",
		reason: "Needed for scheduled functions."
	}];
	return func;
}

//#endregion
exports.getOpts = getOpts;
exports.onSchedule = onSchedule;
Object.defineProperty(exports, 'scheduler_exports', {
  enumerable: true,
  get: function () {
    return scheduler_exports;
  }
});