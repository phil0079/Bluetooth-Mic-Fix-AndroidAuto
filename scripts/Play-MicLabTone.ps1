param([int]$DeviceId = -1, [switch]$SetupConfirmed)
$ErrorActionPreference = 'Stop'
if ($DeviceId -ge 0 -and !$SetupConfirmed) { throw 'User must confirm physical setup before playback. Supply -SetupConfirmed only after confirmation.' }
# Default invocation only lists outputs. Playback requires explicit device ID and user setup confirmation.
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Threading;
public static class MicLabWave {
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
  public struct Caps { public ushort mid,pid; public uint version; [MarshalAs(UnmanagedType.ByValTStr, SizeConst=32)] public string name; public uint formats; public ushort channels,reserved; public uint support; }
  [StructLayout(LayoutKind.Sequential, Pack=2)]
  public struct Format { public ushort tag,channels; public uint rate,bytes; public ushort align,bits,extra; }
  [StructLayout(LayoutKind.Sequential)]
  public struct Header { public IntPtr data; public uint length,recorded; public UIntPtr user; public uint flags,loops; public IntPtr next; public UIntPtr reserved; }
  [DllImport("winmm.dll")] static extern uint waveOutGetNumDevs();
  [DllImport("winmm.dll", CharSet=CharSet.Unicode)] static extern uint waveOutGetDevCapsW(UIntPtr id,out Caps caps,uint size);
  [DllImport("winmm.dll")] static extern uint waveOutOpen(out IntPtr handle,uint id,ref Format format,IntPtr callback,IntPtr instance,uint flags);
  [DllImport("winmm.dll")] static extern uint waveOutPrepareHeader(IntPtr handle,IntPtr header,uint size);
  [DllImport("winmm.dll")] static extern uint waveOutWrite(IntPtr handle,IntPtr header,uint size);
  [DllImport("winmm.dll")] static extern uint waveOutReset(IntPtr handle);
  [DllImport("winmm.dll")] static extern uint waveOutUnprepareHeader(IntPtr handle,IntPtr header,uint size);
  [DllImport("winmm.dll")] static extern uint waveOutClose(IntPtr handle);
  static void Check(uint code) { if(code!=0) throw new Exception("waveOut error "+code); }
  public static string[] List() {
    var list=new string[waveOutGetNumDevs()];
    for(uint i=0;i<list.Length;i++) { Caps c; Check(waveOutGetDevCapsW((UIntPtr)i,out c,(uint)Marshal.SizeOf(typeof(Caps)))); list[i]=i+": "+c.name; }
    return list;
  }
  public static void Play(uint id) {
    Caps c; Check(waveOutGetDevCapsW((UIntPtr)id,out c,(uint)Marshal.SizeOf(typeof(Caps))));
    if(c.name.IndexOf("Speaker",StringComparison.OrdinalIgnoreCase)<0) throw new Exception("Choose a physical speaker output, not headset/communications audio.");
    const int rate=48000, seconds=12;
    var samples=new short[rate*seconds];
    for(int i=0;i<samples.Length;i++) {
      double t=(double)i/rate, phase=t%4;
      // 0-1s silent; 1-3s 997Hz; 3-4s silent. Three identical cycles.
      double envelope=phase>=1 && phase<3 ? Math.Min(1,Math.Min((phase-1)/.02,(3-phase)/.02)) : 0;
      samples[i]=(short)(32767*.08*envelope*Math.Sin(2*Math.PI*997*t));
    }
    var pin=GCHandle.Alloc(samples,GCHandleType.Pinned);
    IntPtr handle=IntPtr.Zero,header=IntPtr.Zero;
    bool prepared=false;
    uint size=(uint)Marshal.SizeOf(typeof(Header));
    try {
      var format=new Format{tag=1,channels=1,rate=rate,bytes=rate*2,align=2,bits=16,extra=0};
      Check(waveOutOpen(out handle,id,ref format,IntPtr.Zero,IntPtr.Zero,0));
      header=Marshal.AllocHGlobal((int)size);
      Marshal.StructureToPtr(new Header{data=pin.AddrOfPinnedObject(),length=(uint)samples.Length*2},header,false);
      Check(waveOutPrepareHeader(handle,header,size)); prepared=true;
      Console.WriteLine("Tone start UTC="+DateTime.UtcNow.ToString("o")+" output="+c.name+" pattern=1s silence,2s 997Hz,1s silence x3; PCM peak=0.08");
      Check(waveOutWrite(handle,header,size));
      var timer=System.Diagnostics.Stopwatch.StartNew();
      while((Marshal.PtrToStructure<Header>(header).flags&1)==0 && timer.ElapsedMilliseconds<15000) Thread.Sleep(30);
      Console.WriteLine("Tone ended UTC="+DateTime.UtcNow.ToString("o"));
    } finally {
      if(handle!=IntPtr.Zero) { waveOutReset(handle); if(prepared) waveOutUnprepareHeader(handle,header,size); waveOutClose(handle); }
      if(header!=IntPtr.Zero) Marshal.FreeHGlobal(header);
      pin.Free();
    }
  }
}
'@
if ($DeviceId -lt 0) { [MicLabWave]::List() } else { [MicLabWave]::Play([uint32]$DeviceId) }
