const { withGradleProperties } = require("expo/config-plugins");

module.exports = (config) =>
  withGradleProperties(config, (config) => {
    config.modResults = config.modResults.filter(
      (item) => item.key !== "android.minSdkVersion",
    );
    config.modResults.push({
      type: "property",
      key: "android.minSdkVersion",
      value: "30",
    });
    return config;
  });
