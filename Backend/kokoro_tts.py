import argparse
import json
import os
import sys
import numpy as np
import soundfile as sf
import torch
from kokoro import KPipeline

def get_voice(speaker, voice_map, default_voices):
    if not speaker:
        return default_voices[0]
    sp_lower = speaker.lower().strip()
    # Direct mapping
    if 'host a' in sp_lower or 'host 1' in sp_lower or 'hosta' in sp_lower or 'host1' in sp_lower:
        return 'af_heart'
    if 'host b' in sp_lower or 'host 2' in sp_lower or 'hostb' in sp_lower or 'host2' in sp_lower:
        return 'am_adam'
    if 'heart' in sp_lower:
        return 'af_heart'
    if 'adam' in sp_lower:
        return 'am_adam'
    
    # Assign dynamically if seen speaker
    if speaker not in voice_map:
        assigned_index = len(voice_map) % len(default_voices)
        voice_map[speaker] = default_voices[assigned_index]
    return voice_map[speaker]

def main():
    parser = argparse.ArgumentParser(description="Kokoro Multi-Speaker Text-to-Speech script")
    parser.add_argument("--input", required=True, help="Path to input text or JSON file")
    parser.add_argument("--output", required=True, help="Path to output WAV file")
    parser.add_argument("--voice", default="af_heart", help="Default voice to use")
    
    args = parser.parse_args()
    
    try:
        if not os.path.exists(args.input):
            print(f"Error: Input file '{args.input}' does not exist.", file=sys.stderr)
            sys.exit(1)
            
        print("Initializing KPipeline...")
        pipeline = KPipeline(lang_code='a')
        
        with open(args.input, "r", encoding="utf-8") as f:
            content = f.read().strip()
            
        segments = []
        is_json = False
        
        try:
            data = json.loads(content)
            if isinstance(data, list):
                segments = data
                is_json = True
            elif isinstance(data, dict):
                if "segments" in data and isinstance(data["segments"], list):
                    segments = data["segments"]
                    is_json = True
        except json.JSONDecodeError:
            pass
            
        audio_segments = []
        sample_rate = 24000
        
        if is_json and segments:
            print(f"Detected JSON input. Generating {len(segments)} segment(s)...")
            voice_map = {}
            default_voices = ['af_heart', 'am_adam']
            
            for idx, seg in enumerate(segments):
                speaker = seg.get("speaker") or seg.get("host") or seg.get("Speaker") or seg.get("Host") or ""
                text = seg.get("line") or seg.get("text") or seg.get("Line") or seg.get("Text") or ""
                
                if not text.strip():
                    continue
                    
                voice = get_voice(speaker, voice_map, default_voices)
                print(f"Generating segment {idx + 1}/{len(segments)}: speaker='{speaker}' voice='{voice}' text='{text}'")
                
                generator = pipeline(text, voice=voice, speed=1.0)
                seg_audio_parts = []
                for gs, ps, audio in generator:
                    if hasattr(audio, 'numpy'):
                        audio_np = audio.numpy()
                    elif isinstance(audio, torch.Tensor):
                        audio_np = audio.cpu().numpy()
                    else:
                        audio_np = np.array(audio)
                    seg_audio_parts.append(audio_np)
                
                if seg_audio_parts:
                    segment_audio = np.concatenate(seg_audio_parts)
                    audio_segments.append(segment_audio)
                    # Add 0.5s pause/silence between speaker turns
                    if idx < len(segments) - 1:
                        silence = np.zeros(int(0.5 * sample_rate), dtype=np.float32)
                        audio_segments.append(silence)
        else:
            print("Detected plain text input. Generating single voice audio...")
            generator = pipeline(content, voice=args.voice, speed=1.0)
            for gs, ps, audio in generator:
                if hasattr(audio, 'numpy'):
                    audio_np = audio.numpy()
                elif isinstance(audio, torch.Tensor):
                    audio_np = audio.cpu().numpy()
                else:
                    audio_np = np.array(audio)
                audio_segments.append(audio_np)
                
        if not audio_segments:
            print("Error: No audio segments were generated.", file=sys.stderr)
            sys.exit(1)
            
        print("Concatenating audio segments...")
        final_audio = np.concatenate(audio_segments)
        
        print(f"Writing audio output to {args.output}...")
        sf.write(args.output, final_audio, sample_rate)
        print("Audio generation completed successfully.")
        
    except Exception as e:
        import traceback
        traceback.print_exc(file=sys.stderr)
        print(f"Error during Kokoro TTS script execution: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
