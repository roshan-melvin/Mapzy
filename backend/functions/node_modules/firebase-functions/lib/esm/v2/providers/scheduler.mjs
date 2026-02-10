import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { error } from "../../logger/index.mjs";
import { initV2Endpoint, initV2ScheduleTrigger } from "../../runtime/manifest.mjs";
import { copyIfPresent } from "../../common/encoding.mjs";
import { withInit } from "../../common/onInit.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { getGlobalOptions, optionsToEndpoint } from "../options.mjs";

//#region src/v2/providers/scheduler.ts
var scheduler_exports = /* @__PURE__ */ __export({
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
			error(err.message);
			res.status(500).send();
		}
	};
	const func = wrapTraceContext(withInit(httpFunc));
	func.run = handler;
	const globalOpts = getGlobalOptions();
	const baseOptsEndpoint = optionsToEndpoint(globalOpts);
	const specificOptsEndpoint = optionsToEndpoint(separatedOpts.opts);
	const ep = {
		...initV2Endpoint(globalOpts, separatedOpts.opts),
		platform: "gcfv2",
		...baseOptsEndpoint,
		...specificOptsEndpoint,
		labels: {
			...baseOptsEndpoint?.labels,
			...specificOptsEndpoint?.labels
		},
		scheduleTrigger: initV2ScheduleTrigger(separatedOpts.schedule, globalOpts, separatedOpts.opts)
	};
	copyIfPresent(ep.scheduleTrigger, separatedOpts, "timeZone");
	copyIfPresent(ep.scheduleTrigger.retryConfig, separatedOpts.retryConfig, "retryCount", "maxRetrySeconds", "minBackoffSeconds", "maxBackoffSeconds", "maxDoublings");
	func.__endpoint = ep;
	func.__requiredAPIs = [{
		api: "cloudscheduler.googleapis.com",
		reason: "Needed for scheduled functions."
	}];
	return func;
}

//#endregion
export { getOpts, onSchedule, scheduler_exports };