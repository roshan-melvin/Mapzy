
//#region src/common/change.ts
/** @internal */
function applyFieldMask(sparseBefore, after, fieldMask) {
	const before = { ...after };
	const masks = fieldMask.split(",");
	for (const mask of masks) {
		const parts = mask.split(".");
		const head = parts[0];
		const tail = parts.slice(1).join(".");
		if (parts.length > 1) {
			before[head] = applyFieldMask(sparseBefore?.[head], after[head], tail);
			continue;
		}
		const val = sparseBefore?.[head];
		if (typeof val === "undefined") {
			delete before[mask];
		} else {
			before[mask] = val;
		}
	}
	return before;
}
/**
* The Cloud Functions interface for events that change state, such as
* Realtime Database or Cloud Firestore `onWrite` and `onUpdate` events.
*
* For more information about the format used to construct `Change` objects, see
* {@link ChangeJson} below.
*
*/
var Change = class Change {
	/**
	* Factory method for creating a `Change` from a `before` object and an `after`
	* object.
	*/
	static fromObjects(before, after) {
		return new Change(before, after);
	}
	/**
	* Factory method for creating a `Change` from JSON and an optional customizer
	* function to be applied to both the `before` and the `after` fields.
	*/
	static fromJSON(json, customizer = (x) => x) {
		let before = { ...json.before };
		if (json.fieldMask) {
			before = applyFieldMask(before, json.after, json.fieldMask);
		}
		return Change.fromObjects(customizer(before || {}), customizer(json.after || {}));
	}
	constructor(before, after) {
		this.before = before;
		this.after = after;
	}
};

//#endregion
exports.Change = Change;
exports.applyFieldMask = applyFieldMask;