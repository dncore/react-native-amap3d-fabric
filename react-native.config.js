module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: "lib/android",
        packageImportPath: "import qiuxiang.amap3d.AMap3DPackage;",
        packageInstance: "new AMap3DPackage()",
      },
    },
  },
};
