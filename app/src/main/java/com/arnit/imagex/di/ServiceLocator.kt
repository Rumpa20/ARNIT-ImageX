package com.arnit.imagex.di

import android.content.Context
import com.arnit.imagex.billing.BillingGateway
import com.arnit.imagex.billing.TestModeBillingGateway
import com.arnit.imagex.converter.BatchConverter
import com.arnit.imagex.converter.ConversionEngine
import com.arnit.imagex.converter.TempTank
import com.arnit.imagex.decoder.AndroidPlatformRaster
import com.arnit.imagex.formats.CodecFactory
import com.arnit.imagex.history.HistoryRepository
import com.arnit.imagex.premium.PremiumRepository
import com.arnit.imagex.settings.SettingsRepository
import com.arnit.imagex.storage.TempFileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container (no external framework: keeps the Gradle setup
 * light for on-device builds). All singletons are lazily created here.
 */
object ServiceLocator {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var settingsCache: SettingsRepository? = null
    private var tempCache: TempFileManager? = null
    private var historyCache: HistoryRepository? = null
    private var billingCache: BillingGateway? = null
    private var premiumCache: PremiumRepository? = null
    private var engineCache: ConversionEngine? = null
    private var batchCache: BatchConverter? = null
    private var platformRasterCache: AndroidPlatformRaster? = null

    fun appCoroutineScope(): CoroutineScope = appScope

    fun settings(context: Context): SettingsRepository =
        settingsCache ?: SettingsRepository(context.applicationContext).also { settingsCache = it }

    fun tempFileManager(context: Context): TempFileManager =
        tempCache ?: TempFileManager(context.applicationContext).also { tempCache = it }

    fun history(context: Context): HistoryRepository =
        historyCache ?: HistoryRepository(context.applicationContext).also { historyCache = it }

    fun platformRaster(context: Context): AndroidPlatformRaster =
        platformRasterCache ?: AndroidPlatformRaster().also { platformRasterCache = it }

    fun billing(context: Context): BillingGateway =
        billingCache ?: TestModeBillingGateway(settings(context), appScope).also { billingCache = it }

    fun premium(context: Context): PremiumRepository =
        premiumCache ?: PremiumRepository(billing(context), appScope).also { premiumCache = it }

    fun engine(context: Context): ConversionEngine {
        engineCache?.let { return it }
        val appContext = context.applicationContext
        val platform = platformRaster(appContext)
        val tank = TempTank(platform) { tempFileManager(appContext).dir }
        val engine = ConversionEngine(
            CodecFactory.createDecoders(platform),
            CodecFactory.createEncoders(platform),
            tank
        )
        engineCache = engine
        return engine
    }

    fun batch(context: Context): BatchConverter =
        batchCache ?: BatchConverter(engine(context)).also { batchCache = it }
}
