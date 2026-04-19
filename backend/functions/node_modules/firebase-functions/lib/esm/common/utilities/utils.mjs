//#region src/common/utilities/utils.ts
function isObject(obj) {
	return typeof obj === "object" && !!obj;
}
/** @hidden */
function applyChange(src, dest) {
	if (!isObject(dest) || !isObject(src)) {
		return dest;
	}
	return merge(src, dest);
}
function merge(src, dest) {
	const res = {};
	const keys = new Set([...Object.keys(src), ...Object.keys(dest)]);
	for (const key of keys.values()) {
		if (key in dest) {
			if (dest[key] === null) {
				continue;
			}
			res[key] = applyChange(src[key], dest[key]);
		} else if (src[key] !== null) {
			res[key] = src[key];
		}
	}
	return res;
}

//#endregion
export { applyChange };