// SPDX-License-Identifier: GPL-3.0-or-later

using System.Runtime.InteropServices;
using Meltype.Composition;

namespace Meltype.Android;

/// <summary>
/// 本家の Exports.cs (Mac 版・Linux 版の C ABI) に無い、Android 版で使う関数。
/// </summary>
public static class AndroidExports
{
    /// <summary>
    /// 入力を覚えないように求める入力欄か (1) どうか (0)。Android の IME_FLAG_NO_PERSONALIZED_LEARNING
    /// (Chrome のシークレットタブなど)。本家 Windows 版のシークレット / InPrivate ウィンドウと同じく、入力は
    /// 普通に動かして学習だけを止める (MeltypeSession.Private)。
    /// </summary>
    [UnmanagedCallersOnly(EntryPoint = "meltype_set_private")]
    public static void SetPrivate(IntPtr handle, int enabled)
    {
        if (handle != IntPtr.Zero && GCHandle.FromIntPtr(handle).Target is MeltypeSession session) session.Private = enabled != 0;
    }

    /// <summary>
    /// Meltype の外でキャレットが動いた (アプリに渡したキー・タップで別の場所に動かした) とき。
    /// 前に確定した語を、次の語の文脈で確定し直さないようにする (MeltypeSession.ForgetLastCommit)。
    /// </summary>
    [UnmanagedCallersOnly(EntryPoint = "meltype_forget_last_commit")]
    public static void ForgetLastCommit(IntPtr handle)
    {
        if (handle != IntPtr.Zero && GCHandle.FromIntPtr(handle).Target is MeltypeSession session) session.ForgetLastCommit();
    }
}
