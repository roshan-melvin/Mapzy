//#region src/common/debug.ts
const debugMode = process.env.FIREBASE_DEBUG_MODE === "true";
function loadDebugFeatures() {
	if (!debugMode) {
		return {};
	}
	try {
		const obj = JSON.parse(process.env.FIREBASE_DEBUG_FEATURES);
		if (typeof obj !== "object") {
			return {};
		}
		return obj;
	} catch (_e) {
		return {};
	}
}
function debugFeatureValue(feat) {
	if (!debugMode) {
		return;
	}
	return loadDebugFeatures()[feat];
}
function isDebugFeatureEnabled(feat) {
	return debugMode && !!debugFeatureValue(feat);
}

//#endregion
export { debugFeatureValue, isDebugFeatureEnabled };