package dev.ftblib.verification;

import com.cleanroommc.client.ime.WindowsIMEHandler;
import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinUser.*;
import com.sun.jna.win32.StdCallLibrary;
import java.util.Objects;

/** Exercises the exact Cleanroom 0.4.0 native IMM handler on a real Win32 window. */
public final class WindowsImeCheck
{
    public interface Imm extends StdCallLibrary {
        Imm API=Native.load("imm32",Imm.class);
        Pointer ImmGetContext(HWND window);
        boolean ImmReleaseContext(HWND window,Pointer context);
        Pointer ImmAssociateContext(HWND window,Pointer context);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static boolean associated(HWND window) {
        Pointer context=Imm.API.ImmGetContext(window);
        if(context==null)return false;
        require(Imm.API.ImmReleaseContext(window,context),"ImmReleaseContext failed");return true;
    }
    public static void main(String[] args)throws Exception {
        User32 user=User32.INSTANCE;HINSTANCE instance=Kernel32.INSTANCE.GetModuleHandle(null);
        WNDCLASSEX clazz=new WNDCLASSEX();clazz.hInstance=instance;clazz.lpszClassName="FTBLibImeRegression";
        WindowProc procedure=(window,message,wparam,lparam)->user.DefWindowProc(window,message,wparam,lparam);
        clazz.lpfnWndProc=procedure;
        require(user.RegisterClassEx(clazz).intValue()!=0,"Could not register Win32 window class");
        HWND window=user.CreateWindowEx(0,clazz.lpszClassName,"FTB Library IME regression",0x00cf0000,40,40,480,160,null,null,instance,null);
        require(window!=null,"Could not create native window");
        try {
            user.ShowWindow(window,5);user.SetForegroundWindow(window);user.SetFocus(window);
            MSG message=new MSG();
            for(int i=0;i<40&&!Objects.equals(user.GetForegroundWindow(),window);i++){
                while(user.PeekMessage(message,null,0,0,1)){user.TranslateMessage(message);user.DispatchMessage(message);}
                user.SetForegroundWindow(window);user.SetFocus(window);Thread.sleep(50);
            }
            require(Objects.equals(user.GetForegroundWindow(),window),"Runner could not focus the owned native window");
            Pointer previous=Imm.API.ImmAssociateContext(window,null);
            try {
                require(!associated(window),"Native disabled-IME fixture was not established");
                WindowsIMEHandler handler=new WindowsIMEHandler();
                handler.accept(true);require(associated(window),"Baseline IME activation did not associate an input context");
                System.out.println("PASS: real Cleanroom 0.4.0 handler associates the disabled Win32 input context");
                handler.accept(false);require(!associated(window),"Baseline IME deactivation left the context associated");
                handler.accept(true);require(associated(window),"Baseline IME did not reactivate after blur/refocus");
                handler.accept(false);require(!associated(window),"Final native IME blur failed");
                System.out.println("PASS: native IME blur and refocus restore the context without an external editor");
            } finally {Imm.API.ImmAssociateContext(window,previous);}
            System.out.println("WINDOWS_IME_CONTEXT_PASS");
            System.out.println("Boundary: no Microsoft Pinyin candidate keystrokes or Minecraft OpenGL window were automated");
        } finally {user.DestroyWindow(window);user.UnregisterClass(clazz.lpszClassName,instance);}
    }
}
