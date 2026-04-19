import { CONSOLE_SEVERITY, UNPATCHED_CONSOLE } from "./common.mjs";
import { format } from "util";

//#region src/logger/compat.ts
/** @hidden */
function patchedConsole(severity) {
	return function(data, ...args) {
		let message = format(data, ...args);
		if (severity === "ERROR") {
			message = new Error(message).stack || message;
		}
		UNPATCHED_CONSOLE[CONSOLE_SEVERITY[severity]](JSON.stringify({
			severity,
			message
		}));
	};
}
console.debug = patchedConsole("DEBUG");
console.info = patchedConsole("INFO");
console.log = patchedConsole("INFO");
console.warn = patchedConsole("WARNING");
console.error = patchedConsole("ERROR");

//#endregion
export {  };