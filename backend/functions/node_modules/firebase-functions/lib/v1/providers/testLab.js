const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_v1_cloud_functions = require('../cloud-functions.js');

//#region src/v1/providers/testLab.ts
var testLab_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	ClientInfo: () => ClientInfo,
	PROVIDER: () => PROVIDER,
	ResultStorage: () => ResultStorage,
	SERVICE: () => SERVICE,
	TEST_MATRIX_COMPLETE_EVENT_TYPE: () => TEST_MATRIX_COMPLETE_EVENT_TYPE,
	TestMatrix: () => TestMatrix,
	TestMatrixBuilder: () => TestMatrixBuilder,
	_testMatrixWithOpts: () => _testMatrixWithOpts,
	testMatrix: () => testMatrix
});
/** @internal */
const PROVIDER = "google.testing";
/** @internal */
const SERVICE = "testing.googleapis.com";
/** @internal */
const TEST_MATRIX_COMPLETE_EVENT_TYPE = "testMatrix.complete";
/** Handle events related to Test Lab test matrices. */
function testMatrix() {
	return _testMatrixWithOpts({});
}
/** @internal */
function _testMatrixWithOpts(opts) {
	return new TestMatrixBuilder(() => {
		if (!process.env.GCLOUD_PROJECT) {
			throw new Error("process.env.GCLOUD_PROJECT is not set.");
		}
		return "projects/" + process.env.GCLOUD_PROJECT + "/testMatrices/{matrix}";
	}, opts);
}
/** Builder used to create Cloud Functions for Test Lab test matrices events. */
var TestMatrixBuilder = class {
	/** @internal */
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
	}
	/** Handle a TestMatrix that reached a final test state. */
	onComplete(handler) {
		const dataConstructor = (raw) => {
			return new TestMatrix(raw.data);
		};
		return require_v1_cloud_functions.makeCloudFunction({
			provider: PROVIDER,
			eventType: TEST_MATRIX_COMPLETE_EVENT_TYPE,
			triggerResource: this.triggerResource,
			service: SERVICE,
			dataConstructor,
			handler,
			options: this.options
		});
	}
};
/** TestMatrix captures details about a test run. */
var TestMatrix = class {
	/** @internal */
	constructor(data) {
		this.testMatrixId = data.testMatrixId;
		this.createTime = data.timestamp;
		this.state = data.state;
		this.outcomeSummary = data.outcomeSummary;
		this.invalidMatrixDetails = data.invalidMatrixDetails;
		this.resultStorage = new ResultStorage(data.resultStorage);
		this.clientInfo = new ClientInfo(data.clientInfo);
	}
};
/** Information about the client which invoked the test. */
var ClientInfo = class {
	/** @internal */
	constructor(data) {
		this.name = data?.name || "";
		this.details = {};
		for (const detail of data?.clientInfoDetails || []) {
			this.details[detail.key] = detail.value || "";
		}
	}
};
/** Locations where the test results are stored. */
var ResultStorage = class {
	/** @internal */
	constructor(data) {
		this.gcsPath = data?.googleCloudStorage?.gcsPath;
		this.toolResultsHistoryId = data?.toolResultsHistory?.historyId;
		this.toolResultsExecutionId = data?.toolResultsExecution?.executionId;
		this.resultsUrl = data?.resultsUrl;
	}
};

//#endregion
exports.ClientInfo = ClientInfo;
exports.PROVIDER = PROVIDER;
exports.ResultStorage = ResultStorage;
exports.SERVICE = SERVICE;
exports.TEST_MATRIX_COMPLETE_EVENT_TYPE = TEST_MATRIX_COMPLETE_EVENT_TYPE;
exports.TestMatrix = TestMatrix;
exports.TestMatrixBuilder = TestMatrixBuilder;
exports._testMatrixWithOpts = _testMatrixWithOpts;
Object.defineProperty(exports, 'testLab_exports', {
  enumerable: true,
  get: function () {
    return testLab_exports;
  }
});
exports.testMatrix = testMatrix;