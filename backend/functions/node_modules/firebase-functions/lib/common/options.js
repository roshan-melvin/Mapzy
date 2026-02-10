
//#region src/common/options.ts
const RESET_VALUE_TAG = Symbol.for("firebase-functions:ResetValue:Tag");
/**
* Special configuration type to reset configuration to platform default.
*
* @alpha
*/
var ResetValue = class ResetValue {
	/**
	* Handle the "Dual-Package Hazard".
	*
	* We implement custom `Symbol.hasInstance` to so CJS/ESM ResetValue instances
	* are recognized as the same type.
	*/
	static [Symbol.hasInstance](instance) {
		return instance?.[RESET_VALUE_TAG] === true;
	}
	get [RESET_VALUE_TAG]() {
		return true;
	}
	toJSON() {
		return null;
	}
	constructor() {}
	static getInstance() {
		return new ResetValue();
	}
};
/**
* Special configuration value to reset configuration to platform default.
*/
const RESET_VALUE = ResetValue.getInstance();

//#endregion
exports.RESET_VALUE = RESET_VALUE;
exports.ResetValue = ResetValue;