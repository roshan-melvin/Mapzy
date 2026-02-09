import { __export } from "../_virtual/rolldown_runtime.mjs";
import { traceContext } from "../common/trace.mjs";
import { CONSOLE_SEVERITY, UNPATCHED_CONSOLE } from "./common.mjs";
import { format } from "util";

//#region src/logger/index.ts
var logger_exports = /* @__PURE__ */ __export({
	debug: () => debug,
	error: () => error,
	info: () => info,
	log: () => log,
	logger: () => logger,
	warn: () => warn,
	write: () => write
});
/** @internal */
function removeCircular(obj, refs = new Set()) {
	if (typeof obj !== "object" || !obj) {
		return obj;
	}
	if (obj.toJSON && typeof obj.toJSON === "function") {
		return obj.toJSON();
	}
	if (refs.has(obj)) {
		return "[Circular]";
	} else {
		refs.add(obj);
	}
	let returnObj;
	if (Array.isArray(obj)) {
		returnObj = new Array(obj.length);
	} else {
		returnObj = {};
	}
	for (const k in obj) {
		if (obj.hasOwnProperty(k)) {
			try {
				if (refs.has(obj[k])) {
					returnObj[k] = "[Circular]";
				} else {
					returnObj[k] = removeCircular(obj[k], refs);
				}
			} catch {
				returnObj[k] = "[Error - cannot serialize]";
			}
		} else {
			returnObj[k] = "[Error - defined in the prototype but missing in the object]";
		}
	}
	refs.delete(obj);
	return returnObj;
}
/**
* Writes a `LogEntry` to `stdout`/`stderr` (depending on severity).
* @param entry - The `LogEntry` including severity, message, and any additional structured metadata.
* @public
*/
function write(entry) {
	const ctx = traceContext.getStore();
	if (ctx?.traceId) {
		entry["logging.googleapis.com/trace"] = `projects/${process.env.GCLOUD_PROJECT}/traces/${ctx.traceId}`;
	}
	UNPATCHED_CONSOLE[CONSOLE_SEVERITY[entry.severity]](JSON.stringify(removeCircular(entry)));
}
/**
* Writes a `DEBUG` severity log. If the last argument provided is a plain object,
* it is added to the `jsonPayload` in the Cloud Logging entry.
* @param args - Arguments, concatenated into the log message with space separators.
* @public
*/
function debug(...args) {
	write(entryFromArgs("DEBUG", args));
}
/**
* Writes an `INFO` severity log. If the last argument provided is a plain object,
* it is added to the `jsonPayload` in the Cloud Logging entry.
* @param args - Arguments, concatenated into the log message with space separators.
* @public
*/
function log(...args) {
	write(entryFromArgs("INFO", args));
}
/**
* Writes an `INFO` severity log. If the last argument provided is a plain object,
* it is added to the `jsonPayload` in the Cloud Logging entry.
* @param args - Arguments, concatenated into the log message with space separators.
* @public
*/
function info(...args) {
	write(entryFromArgs("INFO", args));
}
/**
* Writes a `WARNING` severity log. If the last argument provided is a plain object,
* it is added to the `jsonPayload` in the Cloud Logging entry.
* @param args - Arguments, concatenated into the log message with space separators.
* @public
*/
function warn(...args) {
	write(entryFromArgs("WARNING", args));
}
/**
* Writes an `ERROR` severity log. If the last argument provided is a plain object,
* it is added to the `jsonPayload` in the Cloud Logging entry.
* @param args - Arguments, concatenated into the log message with space separators.
* @public
*/
function error(...args) {
	write(entryFromArgs("ERROR", args));
}
/** @hidden */
function entryFromArgs(severity, args) {
	let entry = {};
	const lastArg = args[args.length - 1];
	if (lastArg && typeof lastArg === "object" && lastArg.constructor === Object) {
		entry = args.pop();
	}
	let message = format(...args);
	if (severity === "ERROR" && !args.find((arg) => arg instanceof Error)) {
		message = new Error(message).stack || message;
	}
	const out = {
		...entry,
		severity
	};
	if (message) {
		out.message = message;
	}
	return out;
}
/**
* Logger object containing all logging methods.
*
* Mockable for testing purposes.
*/
const logger = {
	write,
	debug,
	log,
	info,
	warn,
	error
};

//#endregion
export { debug, error, info, log, logger, logger_exports, warn, write };