package com.deepfocus.app.data

/**
 * 视频资源目录
 *
 * **视频制作环节已取消**，现在只剩**专注页书房**一段视频。
 *
 * 原因：视频要等模型出片，成本高、体积大（15 秒视频几十 MB）。
 * dsh-pet 有一百多个现成的透明 GIF 动作，转码成 animated WebP 后
 * 登场、喂食、换装全部够用，而且更轻。
 *
 * 唯一还用视频的是**专注页书房**：9:16 全屏铺满 + 环境音
 * （翻书声、鸟鸣、笔尖沙沙），GIF 撑不起这种沉浸感。
 *
 * 动画清单见 `pet/PetAction.kt`（109 个动作）。
 */
object FishAnimationCatalog {

    /**
     * 专注页书房视频（9:16，循环播放）
     *
     * 素材由用户按 docs/专注页视频Prompt.md 生成后放进
     * `artwork/videos/focus/bigfatfish.mp4`，再跑 install_videos.py 安装。
     */
    const val FOCUS_VIDEO_PATH = "videos/focus/bigfatfish.mp4"
}
