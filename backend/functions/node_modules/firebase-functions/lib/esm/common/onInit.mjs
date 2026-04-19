import { warn } from "../logger/index.mjs";

//#region src/common/onInit.ts
let initCallback = null;
let didInit = false;
/**
* Registers a callback that should be run when in a production environment
* before executing any functions code.
* Calling this function more than once leads to undefined behavior.
* @param callback initialization callback to be run before any function executes.
*/
function onInit(callback) {
	if (initCallback) {
		warn("Setting onInit callback more than once. Only the most recent callback will be called");
	}
	initCallback = callback;
	didInit = false;
}
/** @internal */
function withInit(func) {
	return async (...args) => {
		if (!didInit) {
			if (initCallback) {
				await initCallback();
			}
			didInit = true;
		}
		return func(...args);
	};
}

//#endregion
export { onInit, withInit };