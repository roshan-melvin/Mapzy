
//#region src/logger/common.ts
/** @hidden */
const CONSOLE_SEVERITY = {
	DEBUG: "debug",
	INFO: "info",
	NOTICE: "info",
	WARNING: "warn",
	ERROR: "error",
	CRITICAL: "error",
	ALERT: "error",
	EMERGENCY: "error"
};
/** @hidden */
const UNPATCHED_CONSOLE = {
	debug: console.debug,
	info: console.info,
	log: console.log,
	warn: console.warn,
	error: console.error
};

//#endregion
exports.CONSOLE_SEVERITY = CONSOLE_SEVERITY;
exports.UNPATCHED_CONSOLE = UNPATCHED_CONSOLE;