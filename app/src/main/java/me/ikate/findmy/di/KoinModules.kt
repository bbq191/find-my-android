package me.ikate.findmy.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import me.ikate.findmy.data.local.FindMyDatabase
import me.ikate.findmy.data.remote.mqtt.MqttConfig
import me.ikate.findmy.data.repository.AuthRepository
import me.ikate.findmy.data.repository.ContactRepository
import me.ikate.findmy.data.repository.DeviceRepository
import me.ikate.findmy.data.repository.GeofenceRepository
import me.ikate.findmy.service.GeofenceManager
import me.ikate.findmy.ui.screen.contact.ContactViewModel
import me.ikate.findmy.ui.screen.main.MainViewModel
import me.ikate.findmy.util.DeviceIdProvider

/**
 * Koin 应用模块
 * 提供全局单例依赖
 */
val appModule = module {
    // SharedPreferences
    single {
        androidContext().getSharedPreferences("findmy_prefs", android.content.Context.MODE_PRIVATE)
    }

    // Room Database
    single { FindMyDatabase.getInstance(androidContext()) }

    // DAOs
    single { get<FindMyDatabase>().deviceDao() }
    single { get<FindMyDatabase>().contactDao() }
    single { get<FindMyDatabase>().pendingMessageDao() }
    single { get<FindMyDatabase>().geofenceDao() }
}

/**
 * Koin 仓库模块
 * 提供数据仓库依赖
 */
val repositoryModule = module {
    single { AuthRepository(androidContext()) }
    single { DeviceRepository(androidContext()) }
    single { ContactRepository(androidContext()) }
    single { GeofenceRepository(androidContext()) }
}

/**
 * Koin 围栏模块
 * 提供围栏管理相关依赖
 */
val geofenceModule = module {
    single { GeofenceManager(androidContext()) }
}

/**
 * Koin MQTT 模块
 * 提供 MQTT 相关单例依赖
 */
val mqttModule = module {
    single { DeviceRepository.getMqttManager(androidContext()) }
    single { DeviceRepository.getMqttService(androidContext()) }
    single { MqttConfig }
}

/**
 * Koin 服务模块
 * 提供位置、通知等服务依赖
 */
val serviceModule = module {
    single { DeviceIdProvider.getInstance(androidContext()) }
}

/**
 * Koin ViewModel 模块
 * 提供 ViewModel 依赖
 */
val viewModelModule = module {
    viewModel {
        MainViewModel(
            application = androidContext() as android.app.Application,
            authRepository = get(),
            deviceRepository = get(),
            contactRepository = get()
        )
    }

    viewModel {
        ContactViewModel(
            application = androidContext() as android.app.Application,
            authRepository = get(),
            contactRepository = get(),
            deviceRepository = get()
        )
    }
}

/**
 * 所有 Koin 模块
 */
val allModules = listOf(
    appModule,
    repositoryModule,
    mqttModule,
    serviceModule,
    geofenceModule,
    viewModelModule
)
