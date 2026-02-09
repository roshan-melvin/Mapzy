import { pathParts } from "./path.mjs";

//#region src/common/utilities/path-pattern.ts
/** https://cloud.google.com/eventarc/docs/path-patterns */
/** @hidden */
const WILDCARD_CAPTURE_REGEX = new RegExp("{[^/{}]+}", "g");
/** @internal */
function trimParam(param) {
	const paramNoBraces = param.slice(1, -1);
	if (paramNoBraces.includes("=")) {
		return paramNoBraces.slice(0, paramNoBraces.indexOf("="));
	}
	return paramNoBraces;
}
/** @hidden */
var Segment = class {
	constructor(value) {
		this.value = value;
		this.name = "segment";
		this.trimmed = value;
	}
	isSingleSegmentWildcard() {
		return this.value.includes("*") && !this.isMultiSegmentWildcard();
	}
	isMultiSegmentWildcard() {
		return this.value.includes("**");
	}
};
/** @hidden */
var SingleCaptureSegment = class {
	constructor(value) {
		this.value = value;
		this.name = "single-capture";
		this.trimmed = trimParam(value);
	}
	isSingleSegmentWildcard() {
		return true;
	}
	isMultiSegmentWildcard() {
		return false;
	}
};
/** @hidden */
var MultiCaptureSegment = class {
	constructor(value) {
		this.value = value;
		this.name = "multi-capture";
		this.trimmed = trimParam(value);
	}
	isSingleSegmentWildcard() {
		return false;
	}
	isMultiSegmentWildcard() {
		return true;
	}
};
/**
* Implements Eventarc's path pattern from the spec https://cloud.google.com/eventarc/docs/path-patterns
* @internal
*/
var PathPattern = class {
	/** @throws on validation error */
	static compile(rawPath) {
		return undefined;
	}
	constructor(raw) {
		this.raw = raw;
		this.segments = [];
		this.initPathSegments(raw);
	}
	getValue() {
		return this.raw;
	}
	hasWildcards() {
		return this.segments.some((segment) => segment.isSingleSegmentWildcard() || segment.isMultiSegmentWildcard());
	}
	hasCaptures() {
		return this.segments.some((segment) => segment.name === "single-capture" || segment.name === "multi-capture");
	}
	extractMatches(path) {
		const matches = {};
		if (!this.hasCaptures()) {
			return matches;
		}
		const pathSegments = pathParts(path);
		let pathNdx = 0;
		for (let segmentNdx = 0; segmentNdx < this.segments.length && pathNdx < pathSegments.length; segmentNdx++) {
			const segment = this.segments[segmentNdx];
			const remainingSegments = this.segments.length - 1 - segmentNdx;
			const nextPathNdx = pathSegments.length - remainingSegments;
			if (segment.name === "single-capture") {
				matches[segment.trimmed] = pathSegments[pathNdx];
			} else if (segment.name === "multi-capture") {
				matches[segment.trimmed] = pathSegments.slice(pathNdx, nextPathNdx).join("/");
			}
			pathNdx = segment.isMultiSegmentWildcard() ? nextPathNdx : pathNdx + 1;
		}
		return matches;
	}
	initPathSegments(raw) {
		const parts = pathParts(raw);
		for (const part of parts) {
			let segment;
			const capture = part.match(WILDCARD_CAPTURE_REGEX);
			if (capture && capture.length === 1) {
				segment = part.includes("**") ? new MultiCaptureSegment(part) : new SingleCaptureSegment(part);
			} else {
				segment = new Segment(part);
			}
			this.segments.push(segment);
		}
	}
};

//#endregion
export { PathPattern, trimParam };