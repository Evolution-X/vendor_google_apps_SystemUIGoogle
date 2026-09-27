package com.google.android.systemui.smartspace.dagger;

import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.smartspace.SmartspaceTargetFilter;
import com.android.systemui.smartspace.dagger.SmartspaceModule;
import com.android.systemui.smartspace.filters.LockscreenTargetFilter;

import com.google.android.systemui.smartspace.BcSmartspaceDataProvider;
import com.google.android.systemui.smartspace.WeatherSmartspaceDataProvider;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;

@Module
public abstract class SmartspaceGoogleModule {

    /** The BcSmartspaceDataProvider for dreams. */
    @Provides
    @SysUISingleton
    @Named(SmartspaceModule.DREAM_SMARTSPACE_DATA_PLUGIN)
    static BcSmartspaceDataPlugin provideDreamBcSmartspaceDataPlugin() {
        return new BcSmartspaceDataProvider();
    }

    /** The BcSmartspaceDataPlugin for the standalone weather on dream. */
    @Provides
    @SysUISingleton
    @Named(SmartspaceModule.DREAM_WEATHER_SMARTSPACE_DATA_PLUGIN)
    static BcSmartspaceDataPlugin provideDreamWeatherSmartspaceDataPlugin() {
        return new WeatherSmartspaceDataProvider();
    }

    /** The BcSmartspaceDataProvider for the glanceable hub. */
    @Provides
    @SysUISingleton
    @Named(SmartspaceModule.GLANCEABLE_HUB_SMARTSPACE_DATA_PLUGIN)
    static BcSmartspaceDataPlugin provideGlanceableHubBcSmartspaceDataPlugin() {
        return new BcSmartspaceDataProvider();
    }

    /**
     * Target filter for the dream / glanceable hub smartspace controllers. Strips sensitive
     * targets per the lockscreen private-notification setting, same as the lockscreen card.
     * Unscoped: each controller gets its own instance, matching stock.
     */
    @Binds
    @Named(SmartspaceModule.LOCKSCREEN_SMARTSPACE_TARGET_FILTER)
    abstract SmartspaceTargetFilter bindLockscreenSmartspaceTargetFilter(
            LockscreenTargetFilter impl);
}
