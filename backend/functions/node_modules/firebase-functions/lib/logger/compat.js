const require_rolldown_runtime = require('../_virtual/rolldown_runtime.js');
const require_logger_common = require('./common.js');
let util = require("util");
util = require_rolldown_runtime.__toESM(util);

//#region src/logger/compat.ts
/** @hidden */
function patchedConsole(severity) {
	return function(data, ...args) {
		let message = (0, util.format)(data, ...args);
		if (severity === "ERROR") {
			message = new Error(message).stack || message;
		}
		require_logger_common.UNPATCHED_CONSOLE[require_logger_common.CONSOLE_SEVERITY[severity]](JSON.stringify({
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