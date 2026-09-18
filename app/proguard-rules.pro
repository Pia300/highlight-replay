# R8 规则（release 构建开启 minifyEnabled + shrinkResources）。
#
# 应用代码不含反射、序列化框架或 JNI 调用。唯一按方法名派发的是 RemoteViews.setInt
# （NotificationFactory 传入 "setColorFilter"），其宿主视图为框架 ImageView，不受混淆影响。
# AGP 随附的默认规则已覆盖 AndroidX/Compose 与 manifest 声明的组件，
# 因此这里只需保留注解与泛型签名——它们被 Kotlin 元数据与部分库的签名推断间接依赖。
# 引入反射类库（kotlinx.serialization / Room / Gson），或让 RemoteViews 按名称派发到应用类时，
# 必须在此补充对应 keep 规则。
-keepattributes Signature
-keepattributes *Annotation*
