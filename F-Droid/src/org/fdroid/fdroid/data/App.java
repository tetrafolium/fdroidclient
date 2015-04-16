package org.fdroid.fdroid.data;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.fdroid.fdroid.AppFilter;
import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class App extends ValueObject implements Comparable<App> {

    private static final String TAG = "fdroid.App";

    // True if compatible with the device (i.e. if at least one apk is)
    public boolean compatible;
    public boolean includeInRepo = false;

    public String id = "unknown";
    public String name = "Unknown";
    public String summary = "Unknown application";
    public String icon;

    public String description;

    public String license = "Unknown";

    public String webURL;

    public String trackerURL;

    public String sourceURL;

    public String donateURL;

    public String bitcoinAddr;

    public String litecoinAddr;

    public String dogecoinAddr;

    public String flattrID;

    public String upstreamVersion;
    public int upstreamVercode;

    /**
     * Unlike other public fields, this is only accessible via a getter, to
     * emphasise that setting it wont do anything. In order to change this,
     * you need to change suggestedVercode to an apk which is in the apk table.
     */
    private String suggestedVersion;

    public int suggestedVercode;

    public Date added;
    public Date lastUpdated;

    // List of categories (as defined in the metadata
    // documentation) or null if there aren't any.
    public Utils.CommaSeparatedList categories;

    // List of anti-features (as defined in the metadata
    // documentation) or null if there aren't any.
    public Utils.CommaSeparatedList antiFeatures;

    // List of special requirements (such as root privileges) or
    // null if there aren't any.
    public Utils.CommaSeparatedList requirements;

    // True if all updates for this app are to be ignored
    public boolean ignoreAllUpdates;

    // True if the current update for this app is to be ignored
    public int ignoreThisUpdate;

    // Used internally for tracking during repo updates.
    public boolean updated;

    public String iconUrl;

    public String installedVersionName;

    public int installedVersionCode;

    public Apk installedApk; // might be null if not installed

    @Override
    public int compareTo(App app) {
        return name.compareToIgnoreCase(app.name);
    }

    public App() {

    }

    public App(Cursor cursor) {

        checkCursorPosition(cursor);

        for (int i = 0; i < cursor.getColumnCount(); i++) {
            switch (cursor.getColumnName(i)) {
            case AppProvider.DataColumns.IS_COMPATIBLE:
                compatible = cursor.getInt(i) == 1;
                break;
            case AppProvider.DataColumns.APP_ID:
                id = cursor.getString(i);
                break;
            case AppProvider.DataColumns.NAME:
                name = cursor.getString(i);
                break;
            case AppProvider.DataColumns.SUMMARY:
                summary = cursor.getString(i);
                break;
            case AppProvider.DataColumns.ICON:
                icon = cursor.getString(i);
                break;
            case AppProvider.DataColumns.DESCRIPTION:
                description = cursor.getString(i);
                break;
            case AppProvider.DataColumns.LICENSE:
                license = cursor.getString(i);
                break;
            case AppProvider.DataColumns.WEB_URL:
                webURL = cursor.getString(i);
                break;
            case AppProvider.DataColumns.TRACKER_URL:
                trackerURL = cursor.getString(i);
                break;
            case AppProvider.DataColumns.SOURCE_URL:
                sourceURL = cursor.getString(i);
                break;
            case AppProvider.DataColumns.DONATE_URL:
                donateURL = cursor.getString(i);
                break;
            case AppProvider.DataColumns.BITCOIN_ADDR:
                bitcoinAddr = cursor.getString(i);
                break;
            case AppProvider.DataColumns.LITECOIN_ADDR:
                litecoinAddr = cursor.getString(i);
                break;
            case AppProvider.DataColumns.DOGECOIN_ADDR:
                dogecoinAddr = cursor.getString(i);
                break;
            case AppProvider.DataColumns.FLATTR_ID:
                flattrID = cursor.getString(i);
                break;
            case AppProvider.DataColumns.SuggestedApk.VERSION:
                suggestedVersion = cursor.getString(i);
                break;
            case AppProvider.DataColumns.SUGGESTED_VERSION_CODE:
                suggestedVercode = cursor.getInt(i);
                break;
            case AppProvider.DataColumns.UPSTREAM_VERSION_CODE:
                upstreamVercode = cursor.getInt(i);
                break;
            case AppProvider.DataColumns.UPSTREAM_VERSION:
                upstreamVersion = cursor.getString(i);
                break;
            case AppProvider.DataColumns.ADDED:
                added = Utils.parseDate(cursor.getString(i), null);
                break;
            case AppProvider.DataColumns.LAST_UPDATED:
                lastUpdated = Utils.parseDate(cursor.getString(i), null);
                break;
            case AppProvider.DataColumns.CATEGORIES:
                categories = Utils.CommaSeparatedList.make(cursor.getString(i));
                break;
            case AppProvider.DataColumns.ANTI_FEATURES:
                antiFeatures = Utils.CommaSeparatedList.make(cursor.getString(i));
                break;
            case AppProvider.DataColumns.REQUIREMENTS:
                requirements = Utils.CommaSeparatedList.make(cursor.getString(i));
                break;
            case AppProvider.DataColumns.IGNORE_ALLUPDATES:
                ignoreAllUpdates = cursor.getInt(i) == 1;
                break;
            case AppProvider.DataColumns.IGNORE_THISUPDATE:
                ignoreThisUpdate = cursor.getInt(i);
                break;
            case AppProvider.DataColumns.ICON_URL:
                iconUrl = cursor.getString(i);
                break;
            case AppProvider.DataColumns.InstalledApp.VERSION_CODE:
                installedVersionCode = cursor.getInt(i);
                break;
            case AppProvider.DataColumns.InstalledApp.VERSION_NAME:
                installedVersionName = cursor.getString(i);
                break;
            }
        }
    }

    /**
     * Instantiate from a locally installed package.
     */
    @TargetApi(9)
    public App(Context context, PackageManager pm, String packageName)
            throws CertificateEncodingException, IOException, PackageManager.NameNotFoundException {
        final ApplicationInfo appInfo = pm.getApplicationInfo(packageName,
                PackageManager.GET_META_DATA);
        final PackageInfo packageInfo = pm.getPackageInfo(packageName,
                PackageManager.GET_SIGNATURES | PackageManager.GET_PERMISSIONS);

        final String installerPackageName = pm.getInstallerPackageName(packageName);
        CharSequence installerPackageLabel = null;
        if (!TextUtils.isEmpty(installerPackageName)) {
            try {
                ApplicationInfo installerAppInfo = pm.getApplicationInfo(installerPackageName,
                        PackageManager.GET_META_DATA);
                installerPackageLabel = installerAppInfo.loadLabel(pm);
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, e.getMessage());
            }
        }
        if (TextUtils.isEmpty(installerPackageLabel))
            installerPackageLabel = installerPackageName;

        final CharSequence appDescription = appInfo.loadDescription(pm);
        if (TextUtils.isEmpty(appDescription))
            this.summary = "(installed by " + installerPackageLabel + ")";
        else
            this.summary = (String) appDescription.subSequence(0, 40);
        this.id = appInfo.packageName;
        if (Build.VERSION.SDK_INT > 8) {
            this.added = new Date(packageInfo.firstInstallTime);
            this.lastUpdated = new Date(packageInfo.lastUpdateTime);
        } else {
            this.added = new Date(System.currentTimeMillis());
            this.lastUpdated = this.added;
        }
        this.description = "<p>";
        if (!TextUtils.isEmpty(appDescription))
            this.description += appDescription + "\n";
        this.description += "(installed by " + installerPackageLabel
                + ", first installed on " + this.added
                + ", last updated on " + this.lastUpdated + ")</p>";

        this.name = (String) appInfo.loadLabel(pm);

        SanitizedFile apkFile = SanitizedFile.knownSanitized(appInfo.publicSourceDir);
        Apk apk = new Apk();
        apk.version = packageInfo.versionName;
        apk.vercode = packageInfo.versionCode;
        apk.hashType = "sha256";
        apk.hash = Utils.getBinaryHash(apkFile, apk.hashType);
        apk.added = this.added;
        apk.minSdkVersion = Utils.getMinSdkVersion(context, packageName);
        apk.id = this.id;
        apk.installedFile = apkFile;
        if (packageInfo.requestedPermissions == null)
            apk.permissions = null;
        else
            apk.permissions = Utils.CommaSeparatedList.make(
                    Arrays.asList(packageInfo.requestedPermissions));
        apk.apkName = apk.id + "_" + apk.vercode + ".apk";

        final FeatureInfo[] features = packageInfo.reqFeatures;

        if (features != null && features.length > 0) {
            List<String> featureNames = new ArrayList<>(features.length);

            for (FeatureInfo feature : features) {
                featureNames.add(feature.name);
            }

            apk.features = Utils.CommaSeparatedList.make(featureNames);
        }

        // Signature[] sigs = pkgInfo.signatures;

        byte[] rawCertBytes;

        JarFile apkJar = new JarFile(apkFile);
        JarEntry aSignedEntry = (JarEntry) apkJar.getEntry("AndroidManifest.xml");

        if (aSignedEntry == null) {
            apkJar.close();
            throw new CertificateEncodingException("null signed entry!");
        }

        // Due to a bug in android 5.0 lollipop, the inclusion of BouncyCastle causes
        // breakage when verifying the signature of most .jars. For more
        // details, check out https://gitlab.com/fdroid/fdroidclient/issues/111.
        try {
            FDroidApp.disableSpongyCastleOnLollipop();
            InputStream tmpIn = apkJar.getInputStream(aSignedEntry);
            byte[] buff = new byte[2048];
            while (tmpIn.read(buff, 0, buff.length) != -1) {
                /*
                 * NOP - apparently have to READ from the JarEntry before you can
                 * call getCerficates() and have it return != null. Yay Java.
                 */
            }
            tmpIn.close();

            if (aSignedEntry.getCertificates() == null
                    || aSignedEntry.getCertificates().length == 0) {
                apkJar.close();
                throw new CertificateEncodingException("No Certificates found!");
            }

            Certificate signer = aSignedEntry.getCertificates()[0];
            rawCertBytes = signer.getEncoded();
        } finally {
            FDroidApp.enableSpongyCastleOnLollipop();
        }
        apkJar.close();

        /*
         * I don't fully understand the loop used here. I've copied it verbatim
         * from getsig.java bundled with FDroidServer. I *believe* it is taking
         * the raw byte encoding of the certificate & converting it to a byte
         * array of the hex representation of the original certificate byte
         * array. This is then MD5 sum'd. It's a really bad way to be doing this
         * if I'm right... If I'm not right, I really don't know! see lines
         * 67->75 in getsig.java bundled with Fdroidserver
         */
        byte[] fdroidSig = new byte[rawCertBytes.length * 2];
        for (int j = 0; j < rawCertBytes.length; j++) {
            byte v = rawCertBytes[j];
            int d = (v >> 4) & 0xF;
            fdroidSig[j * 2] = (byte) (d >= 10 ? ('a' + d - 10) : ('0' + d));
            d = v & 0xF;
            fdroidSig[j * 2 + 1] = (byte) (d >= 10 ? ('a' + d - 10) : ('0' + d));
        }
        apk.sig = Utils.hashBytes(fdroidSig, "md5");

        this.installedApk = apk;
    }

    public boolean isValid() {
        if (TextUtils.isEmpty(this.name)
                || TextUtils.isEmpty(this.id))
            return false;

        if (this.installedApk == null)
            return false;

        if (TextUtils.isEmpty(this.installedApk.sig))
            return false;

        File apkFile = this.installedApk.installedFile;
        if (apkFile == null || !apkFile.canRead())
            return false;

        return true;
    }

    public ContentValues toContentValues() {

        ContentValues values = new ContentValues();
        values.put(AppProvider.DataColumns.APP_ID, id);
        values.put(AppProvider.DataColumns.NAME, name);
        values.put(AppProvider.DataColumns.SUMMARY, summary);
        values.put(AppProvider.DataColumns.ICON, icon);
        values.put(AppProvider.DataColumns.ICON_URL, iconUrl);
        values.put(AppProvider.DataColumns.DESCRIPTION, description);
        values.put(AppProvider.DataColumns.LICENSE, license);
        values.put(AppProvider.DataColumns.WEB_URL, webURL);
        values.put(AppProvider.DataColumns.TRACKER_URL, trackerURL);
        values.put(AppProvider.DataColumns.SOURCE_URL, sourceURL);
        values.put(AppProvider.DataColumns.DONATE_URL, donateURL);
        values.put(AppProvider.DataColumns.BITCOIN_ADDR, bitcoinAddr);
        values.put(AppProvider.DataColumns.LITECOIN_ADDR, litecoinAddr);
        values.put(AppProvider.DataColumns.DOGECOIN_ADDR, dogecoinAddr);
        values.put(AppProvider.DataColumns.FLATTR_ID, flattrID);
        values.put(AppProvider.DataColumns.ADDED, Utils.formatDate(added, ""));
        values.put(AppProvider.DataColumns.LAST_UPDATED, Utils.formatDate(lastUpdated, ""));
        values.put(AppProvider.DataColumns.SUGGESTED_VERSION_CODE, suggestedVercode);
        values.put(AppProvider.DataColumns.UPSTREAM_VERSION, upstreamVersion);
        values.put(AppProvider.DataColumns.UPSTREAM_VERSION_CODE, upstreamVercode);
        values.put(AppProvider.DataColumns.CATEGORIES, Utils.CommaSeparatedList.str(categories));
        values.put(AppProvider.DataColumns.ANTI_FEATURES, Utils.CommaSeparatedList.str(antiFeatures));
        values.put(AppProvider.DataColumns.REQUIREMENTS, Utils.CommaSeparatedList.str(requirements));
        values.put(AppProvider.DataColumns.IS_COMPATIBLE, compatible ? 1 : 0);
        values.put(AppProvider.DataColumns.IGNORE_ALLUPDATES, ignoreAllUpdates ? 1 : 0);
        values.put(AppProvider.DataColumns.IGNORE_THISUPDATE, ignoreThisUpdate);
        values.put(AppProvider.DataColumns.ICON_URL, iconUrl);

        return values;
    }

    public boolean isInstalled() {
        return installedVersionCode > 0;
    }

    /**
     * True if there are new versions (apks) available
     */
    public boolean hasUpdates() {
        boolean updates = false;
        if (suggestedVercode > 0) {
            updates = (installedVersionCode > 0 && installedVersionCode < suggestedVercode);
        }
        return updates;
    }

    // True if there are new versions (apks) available and the user wants
    // to be notified about them
    public boolean canAndWantToUpdate() {
        boolean canUpdate = hasUpdates();
        boolean wantsUpdate = !ignoreAllUpdates && ignoreThisUpdate < suggestedVercode;
        return canUpdate && wantsUpdate && !isFiltered();
    }

    // Whether the app is filtered or not based on AntiFeatures and root
    // permission (set in the Settings page)
    public boolean isFiltered() {
        return new AppFilter().filter(this);
    }

    public String getSuggestedVersion() { return suggestedVersion; }
}
