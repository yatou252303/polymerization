package com.wc.polymerization.base

import android.content.Context
import android.support.multidex.MultiDex
import com.alibaba.android.arouter.launcher.ARouter
import com.polymerization.core.corebase.CoreBase
import com.polymerization.core.okhttp.NetWorkManager
import com.polymerization.core.utils.sptool.SpManager


class AppContext : CoreBase(){

    override fun onCreate() {
        super.onCreate()
       initArouter()
        //init sharepreference
        SpManager.setContext(this)
        //初始化网路
        NetWorkManager.getInstance().init()
    }

    //静态常量
    object  Instance {
        var instance=this
    }


    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    private fun initArouter(){

        ARouter.openLog()     // 打印日志
            ARouter.openDebug()   // 开启调试模式(如果在InstantRun模式下运行，必须开启调试模式！全风线上版本需要关闭,否则有安险)


        ARouter.init(this) // 尽可能早，推荐在Application中初始化
    }
}
