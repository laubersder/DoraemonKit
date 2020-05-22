package com.didichuxing.doraemonkit.plugin.classtransformer

import com.didichuxing.doraemonkit.plugin.DoKitExtUtil
import com.didichuxing.doraemonkit.plugin.extension.SlowMethodExt
import com.didichuxing.doraemonkit.plugin.getMethodExitInsnNodes
import com.didiglobal.booster.annotations.Priority
import com.didiglobal.booster.transform.TransformContext
import com.didiglobal.booster.transform.asm.ClassTransformer
import com.didiglobal.booster.transform.asm.asIterable
import com.didiglobal.booster.transform.asm.className
import com.google.auto.service.AutoService
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*

/**
 * ================================================
 * 作    者：jint（金台）
 * 版    本：1.0
 * 创建日期：2020/5/14-18:07
 * 描    述：全局业务代码慢函数  wiki:https://juejin.im/post/5e8d87c4f265da47ad218e6b
 * 修订历史：
 * ================================================
 */
@Priority(3)
@AutoService(ClassTransformer::class)
class GlobalSlowMethodTransformer : ClassTransformer {
    val thresholdTime = DoKitExtUtil.slowMethodExt.normalMethod.thresholdTime

    override fun transform(context: TransformContext, klass: ClassNode): ClassNode {
//        if(klass.className == "com.didichuxing.doraemondemo.App"){
//            println("===GlobalSlowMethodTransformer====transform===")
//        }
        if (!DoKitExtUtil.dokitPluginSwitchOpen()) {
            return klass
        }

        if (!DoKitExtUtil.slowMethodExt.methodSwitch) {
            return klass
        }

        if (DoKitExtUtil.slowMethodExt.strategy == SlowMethodExt.STRATEGY_STACK) {
            return klass
        }

        if (DoKitExtUtil.ignorePackageNames(klass.className)) {
            return klass
        }


        val className = klass.className
        //没有自定义设置插装包名 默认是以applicationId为包名 即全局业务代码插桩
        DoKitExtUtil.slowMethodExt.normalMethod.packageNames.forEach { packageName ->
            //包含在白名单中且不在黑名单中
            if (className.contains(packageName) && notMatchedBlackList(className)) {
                klass.methods.filter { methodNode ->
                    methodNode.name != "<init>"
                }.forEach { methodNode ->
                    methodNode.instructions.asIterable().filterIsInstance(MethodInsnNode::class.java).let { methodInsnNodes ->
                        if (methodInsnNodes.isNotEmpty()) {
                            //方法入口插入
                            methodNode.instructions.insert(createMethodEnterInsnList(className, methodNode.name, methodNode.access))
                            //方法出口插入
                            methodNode.instructions.getMethodExitInsnNodes()?.forEach { methodExitInsnNode ->
                                methodNode.instructions.insertBefore(methodExitInsnNode, createMethodExitInsnList(className, methodNode.name, methodNode.access))
                            }
                        }
                    }
                }
            }
        }
        return klass
    }


    private fun notMatchedBlackList(className: String): Boolean {
        for (strBlack in DoKitExtUtil.slowMethodExt.normalMethod.methodBlacklist) {
            if (className.contains(strBlack)) {
                return false
            }
        }

        return true
    }

    /**
     * 创建慢函数入口指令集
     */
    private fun createMethodEnterInsnList(className: String, methodName: String, access: Int): InsnList {
        val isStaticMethod = access and ACC_STATIC != 0
        val insnList = InsnList()
        if (isStaticMethod) {
            insnList.add(MethodInsnNode(INVOKESTATIC, "com/didichuxing/doraemonkit/aop/MethodCostUtil", "getInstance", "()Lcom/didichuxing/doraemonkit/aop/MethodCostUtil;", false))
            insnList.add(IntInsnNode(SIPUSH, thresholdTime))
            insnList.add(LdcInsnNode("$className&$methodName"))
            insnList.add(MethodInsnNode(INVOKEVIRTUAL, "com/didichuxing/doraemonkit/aop/MethodCostUtil", "recodeStaticMethodCostStart", "(ILjava/lang/String;)V", false))
        } else {
            insnList.add(MethodInsnNode(INVOKESTATIC, "com/didichuxing/doraemonkit/aop/MethodCostUtil", "getInstance", "()Lcom/didichuxing/doraemonkit/aop/MethodCostUtil;", false))
            insnList.add(IntInsnNode(SIPUSH, thresholdTime))
            insnList.add(LdcInsnNode("$className&$methodName"))
            insnList.add(VarInsnNode(ALOAD, 0))
            insnList.add(MethodInsnNode(INVOKEVIRTUAL, "com/didichuxing/doraemonkit/aop/MethodCostUtil", "recodeObjectMethodCostStart", "(ILjava/lang/String;Ljava/lang/Object;)V", false))
        }

        return insnList
    }


    /**
     * 创建慢函数退出时的指令集
     */
    private fun createMethodExitInsnList(className: String, methodName: String, access: Int): InsnList {
        val isStaticMethod = access and ACC_STATIC != 0
        val insnList = InsnList()
        if (isStaticMethod) {
            insnList.add(MethodInsnNode(INVOKESTATIC, "com/didichuxing/doraemonkit/aop/MethodCostUtil", "getInstance", "()Lcom/didichuxing/doraemonkit/aop/MethodCostUtil;", false))
            insnList.add(IntInsnNode(SIPUSH, thresholdTime))
            insnList.add(LdcInsnNode("$className&$methodName"))
            insnList.add(MethodInsnNode(INVOKEVIRTUAL, "com/didichuxing/doraemonkit/aop/MethodCostUtil", "recodeStaticMethodCostEnd", "(ILjava/lang/String;)V", false))
        } else {
            insnList.add(MethodInsnNode(INVOKESTATIC, "com/didichuxing/doraemonkit/aop/MethodCostUtil", "getInstance", "()Lcom/didichuxing/doraemonkit/aop/MethodCostUtil;", false))
            insnList.add(IntInsnNode(SIPUSH, thresholdTime))
            insnList.add(LdcInsnNode("$className&$methodName"))
            insnList.add(VarInsnNode(ALOAD, 0))
            insnList.add(MethodInsnNode(INVOKEVIRTUAL, "com/didichuxing/doraemonkit/aop/MethodCostUtil", "recodeObjectMethodCostEnd", "(ILjava/lang/String;Ljava/lang/Object;)V", false))
        }
        return insnList
    }


}
