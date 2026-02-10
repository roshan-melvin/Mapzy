const require_logger_index = require('../logger/index.js');
const require_params_index = require('../params/index.js');
const require_common_options = require('../common/options.js');
const require_common_config = require('../common/config.js');
const require_common_app = require('../common/app.js');
const require_common_change = require('../common/change.js');
const require_common_onInit = require('../common/onInit.js');
const require_v1_function_configuration = require('./function-configuration.js');
const require_v1_cloud_functions = require('./cloud-functions.js');
const require_v1_config = require('./config.js');
const require_v1_providers_analytics = require('./providers/analytics.js');
const require_v1_providers_auth = require('./providers/auth.js');
const require_v1_providers_database = require('./providers/database.js');
const require_v1_providers_firestore = require('./providers/firestore.js');
const require_v1_providers_https = require('./providers/https.js');
const require_v1_providers_pubsub = require('./providers/pubsub.js');
const require_v1_providers_remoteConfig = require('./providers/remoteConfig.js');
const require_v1_providers_storage = require('./providers/storage.js');
const require_v1_providers_tasks = require('./providers/tasks.js');
const require_v1_providers_testLab = require('./providers/testLab.js');
const require_v1_function_builder = require('./function-builder.js');

//#region src/v1/index.ts
const app = { setEmulatedAdminApp: require_common_app.setApp };

//#endregion
exports.Change = require_common_change.Change;
exports.DEFAULT_FAILURE_POLICY = require_v1_function_configuration.DEFAULT_FAILURE_POLICY;
exports.FunctionBuilder = require_v1_function_builder.FunctionBuilder;
exports.INGRESS_SETTINGS_OPTIONS = require_v1_function_configuration.INGRESS_SETTINGS_OPTIONS;
exports.MAX_NUMBER_USER_LABELS = require_v1_function_configuration.MAX_NUMBER_USER_LABELS;
exports.MAX_TIMEOUT_SECONDS = require_v1_function_configuration.MAX_TIMEOUT_SECONDS;
exports.MIN_TIMEOUT_SECONDS = require_v1_function_configuration.MIN_TIMEOUT_SECONDS;
exports.RESET_VALUE = require_common_options.RESET_VALUE;
exports.SUPPORTED_REGIONS = require_v1_function_configuration.SUPPORTED_REGIONS;
exports.VALID_MEMORY_OPTIONS = require_v1_function_configuration.VALID_MEMORY_OPTIONS;
exports.VPC_EGRESS_SETTINGS_OPTIONS = require_v1_function_configuration.VPC_EGRESS_SETTINGS_OPTIONS;
Object.defineProperty(exports, 'analytics', {
  enumerable: true,
  get: function () {
    return require_v1_providers_analytics.analytics_exports;
  }
});
exports.app = app;
Object.defineProperty(exports, 'auth', {
  enumerable: true,
  get: function () {
    return require_v1_providers_auth.auth_exports;
  }
});
exports.config = require_v1_config.config;
Object.defineProperty(exports, 'database', {
  enumerable: true,
  get: function () {
    return require_v1_providers_database.database_exports;
  }
});
exports.firebaseConfig = require_common_config.firebaseConfig;
Object.defineProperty(exports, 'firestore', {
  enumerable: true,
  get: function () {
    return require_v1_providers_firestore.firestore_exports;
  }
});
Object.defineProperty(exports, 'https', {
  enumerable: true,
  get: function () {
    return require_v1_providers_https.https_exports;
  }
});
Object.defineProperty(exports, 'logger', {
  enumerable: true,
  get: function () {
    return require_logger_index.logger_exports;
  }
});
exports.makeCloudFunction = require_v1_cloud_functions.makeCloudFunction;
exports.onInit = require_common_onInit.onInit;
exports.optionsToEndpoint = require_v1_cloud_functions.optionsToEndpoint;
exports.optionsToTrigger = require_v1_cloud_functions.optionsToTrigger;
Object.defineProperty(exports, 'params', {
  enumerable: true,
  get: function () {
    return require_params_index.params_exports;
  }
});
Object.defineProperty(exports, 'pubsub', {
  enumerable: true,
  get: function () {
    return require_v1_providers_pubsub.pubsub_exports;
  }
});
exports.region = require_v1_function_builder.region;
Object.defineProperty(exports, 'remoteConfig', {
  enumerable: true,
  get: function () {
    return require_v1_providers_remoteConfig.remoteConfig_exports;
  }
});
exports.runWith = require_v1_function_builder.runWith;
Object.defineProperty(exports, 'storage', {
  enumerable: true,
  get: function () {
    return require_v1_providers_storage.storage_exports;
  }
});
Object.defineProperty(exports, 'tasks', {
  enumerable: true,
  get: function () {
    return require_v1_providers_tasks.tasks_exports;
  }
});
Object.defineProperty(exports, 'testLab', {
  enumerable: true,
  get: function () {
    return require_v1_providers_testLab.testLab_exports;
  }
});