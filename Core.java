import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

public class Core
{
	//Font data for CHIP-8 (will be stored in unused section of memory)
	short[] chip8_fontset =
		{ 
				0xF0, 0x90, 0x90, 0x90, 0xF0,  // 0
				0x20, 0x60, 0x20, 0x20, 0x70,  // 1
				0xF0, 0x10, 0xF0, 0x80, 0xF0,  // 2
				0xF0, 0x10, 0xF0, 0x10, 0xF0,  // 3
				0x90, 0x90, 0xF0, 0x10, 0x10,  // 4
				0xF0, 0x80, 0xF0, 0x10, 0xF0,  // 5
				0xF0, 0x80, 0xF0, 0x90, 0xF0,  // 6
				0xF0, 0x10, 0x20, 0x40, 0x40,  // 7
				0xF0, 0x90, 0xF0, 0x90, 0xF0,  // 8
				0xF0, 0x90, 0xF0, 0x10, 0xF0,  // 9
				0xF0, 0x90, 0xF0, 0x90, 0x90,  // A
				0xE0, 0x90, 0xE0, 0x90, 0xE0,  // B
				0xF0, 0x80, 0x80, 0x80, 0xF0,  // C
				0xE0, 0x90, 0x90, 0x90, 0xE0,  // D
				0xF0, 0x80, 0xF0, 0x80, 0xF0,  // E
				0xF0, 0x80, 0xF0, 0x80, 0x80   // F
		};
	
	short[] mem = new short[4096];  //Memory with 4096 locations, byte-addressable and big endian
	short[] V = new short[16];  //Registers V[0] through V[15]
	short[] stack = new short[16];  //Stack with 16 levels
	short stack_ptr = 0;  //Stack pointer
	
	short I = 0;  //Instruction register
	short pc = 0x200;  //Program counter
	
	short sound_timer = 0;
	short delay_timer = 0;
	
	short[][] display = new short[64][32];  //Screen display (each pixel is either on or off)
	short[] keypad = new short[16];  //16-button keypad (each button is either pressed or not)
	
	short opcode = 0;
	
	Random rd = new Random();  //Used by the CXKK instruction
	
	void init()
	{
		/* Originally, the CHIP-8 interpreter would be stored in the first 512 bytes of memory
		 * but we don't need that so we can just store font data in there instead.
		 */
		for (int i = 0; i < chip8_fontset.length; i++)
		{
			mem[i] = chip8_fontset[i];
		}
	}
	
	void load_file(String filename)
	{	
		try
		{
			byte[] f = Files.readAllBytes(Paths.get(filename));
			
			for (int i = 0; i < f.length; i++)
			{
				mem[i + 0x200] = f[i];
			}
		}
		catch (IOException e)
		{
			System.out.println("Error: file could not be read");
		}
	}
	
	void fetch_opcode()
	{
		opcode = mem[pc];  //Retrieve the more significant byte of the instruction
		opcode = (short) (opcode << 8);  //Shift it left by one byte to make room for the less significant byte
		opcode = (short) (opcode | mem[pc + 1]);  //Bitwise OR to get the less significant byte
	}
	
	void run()
	{
		fetch_opcode();
		
		int x = (opcode & 0x0F00) >> 8;  //For instructions in the format _XY_
		int y = (opcode & 0x00F0) >> 4;
		
		switch (opcode & 0xF000)
		{
		case 0x0000:
			switch (opcode & 0x00FF)
			{
			case 0xE0:  //00E0 - clear the screen
				for(short[] row : display)
				{
					Arrays.fill(row, (short) 0);
				}
				break;
				
			case 0xEE:  //00EE - return from subroutine
				pc = stack[stack_ptr];
				stack_ptr--;
				break;
				
			default:
				System.out.println("Error: unrecognized opcode 0x" + Integer.toHexString(opcode & 0xFFFF));
				break;
			}
			break;
			
		case 0x1000:  //1NNN - jump to address NNN
			pc = (short) (opcode & 0x0FFF);
			break;
			
		case 0x2000: //2NNN - call subroutine at NNN
			stack_ptr++;
			stack[stack_ptr] = pc;
			pc = (short) (opcode & 0xFFF);
			break;
			
		case 0x3000:  //3XNN - if Vx == NN, skip the next instruction
			if (V[x] == (opcode & 0xFF))
			{
				pc += 2;
			}
			break;
		
		case 0x4000:  //4XNN - if Vx != NN, skip the next instruction
			if (V[x] != (opcode & 0xFF))
			{
				pc += 2;
			}
			break;
		
		case 0x5000:  //5XY0 - if Vx == Vy, skip the next instruction
			if (V[x] == V[y])
			{
				pc += 2;
			}
			break;
			
		case 0x6000:  //6XNN - set Vx to NN
			V[x] = (short) (opcode & 0xFF);
			break;
			
		case 0x7000:  //7XNN - add NN to Vx
			V[x] += (short) (opcode & 0xFF);
			break;
			
		case 0x8000:
			switch (opcode & 0x000F)
			{
			case 0x0:  //8XY0 - set Vx equal to Vy
				V[x] = V[y];
				break;
				
			case 0x1:  //8XY1 - set Vx equal to Vx BITWISE OR Vy
				V[x] = (short) (V[x] | V[y]);
				break;
				
			case 0x2:  //8XY2 - set Vx equal to Vx BITWISE AND Vy
				V[x] = (short) (V[x] & V[y]);
				break;
				
			case 0x3:  //8XY3 - set Vx equal to Vx BITWISE XOR Vy
				V[x] = (short) (V[x] ^ V[y]);
				break;
				
			case 0x4:  //8XY4 - set Vx equal to the last 8 bits of Vx + Vy, set V[F] to 1 if the result is greater than 255
				V[x] += V[y];
				if (V[x] > 255)
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] = (short) (V[x] & 0xFF);
				break;
				
			case 0x5:  //8XY5 - set Vx equal to Vx - Vy, set V[F] to 1 if Vx > Vy
				if (V[x] > V[y])
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] -= V[y];
				break;
				
			case 0x6:  //8XY6 - set Vx = Vx SHR 1
				if ((V[x] & 1) == 1)
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] = (short) (V[x] >> 1);
				break;
				
			case 0x7:  //8XY7 - set Vx = Vy - Vx, set V[F] to 1 if Vy > Vx
				if (V[x] < V[y])
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] = (short) (V[y] - V[x]);
				break;
				
			case 0xE: //8XYE - set Vx = Vx SHL 1
				if ((V[x] & 1) == 1)
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] = (short) (V[x] << 1);
				break;
				
			default:
				System.out.println("Error: unrecognized opcode 0x" + Integer.toHexString(opcode & 0xFFFF));
				break;
			}
			
			break;
			
		case 0x9000:  //9XY0 - skip next instruction if Vx != Vy
			if (V[x] != V[y])
				pc += 2;
			break;
			
		case 0xA000:  //ANNN - set the value of register I to nnn
			I = (short) (opcode & 0xFFF);
			break;
			
		case 0xB000:  //BNNN - set PC to nnn + V[0]
			pc = (short) ((opcode & 0xFFF) + V[0]);
			break;
			
		case 0xC000:  //CXKK - set Vx to a random number from 0 to 255 BITWISE ANDed with kk
			V[x] = (short) (rd.nextInt(256) & (opcode & 0xFF));
			break;
			
		case 0xD000:  //DXYN - display n-byte sprite starting at mem[I] at (Vx, Vy) (see Cowgod's site for more)
			V[0xF] = 0;
			short pixel_value;
			for (short i = 0; i < (opcode & 0xF); i++)
			{
				for (short j = 0; j < 8; j++)
				{
					pixel_value = (short) ((mem[I + i] >> (7-j)) & 1);
					if (pixel_value != display[(V[x] + j) % 64][(V[y] + i) % 32])
					{
						V[0xF] = 1;
						display[(V[x] + j) % 64][(V[y] + i) % 32] = pixel_value;
					}
				}
			}
			break;
			
		case 0xE000:
			switch (opcode & 0x000F)
			{
			case 0xE:  //EX9E - skip next instruction if key of value Vx is pressed
				if (keypad[V[x]] == 1)
				{
					pc += 2;
				}
				break;
				
			case 0x1:  //EXA1 - skip next instruction if key of value Vx is not pressed
				if (keypad[V[x]] == 0)
				{
					pc += 2;
				}
				break;
				
			default:
				System.out.println("Error: unrecognized opcode 0x" + Integer.toHexString(opcode & 0xFFFF));
				break;
			}
			break;
			
		case 0xF000:
			switch (opcode & 0xFF)
			{
			case 0x07:  //FX07 - set Vx to delay timer
				V[x] = delay_timer;
				break;
				
			case 0x0A:  //FX0A - wait for a key press and store its value in Vx, halting execution until a key is pressed
				boolean key_pressed = false;
				
				for (short i = 0; i < 16; i++)
				{
					if (keypad[i] != 0)
					{
						key_pressed = true;
						V[x] = i;
						break;
					}
				}
				
				//If a key was not pressed, the cycle ends immediately, skipping the incrementing/decrementing of
				//the PC and the timers. The next cycle will be a repeat of this instruction until a key is pressed.
				if (!key_pressed)
				{
					return;
				}
				break;
				
			case 0x15:  //FX15 - set the delay timer equal to the value of V[x]
				delay_timer = V[x];
				break;
				
			case 0x18:  //FX18 - set the sound timer equal to the value of V[x]
				sound_timer = V[x];
				break;
				
			case 0x1E:  //FX1E - set I = I + V[x]
				I += V[x];
				break;
				
			case 0x29:  //FX29 - "The value of I is set to the location for the hexadecimal sprite corresponding to the value of Vx."
				
				break;
				
			default:
				System.out.println("Error: unrecognized opcode 0x" + Integer.toHexString(opcode & 0xFFFF));
				break;
			}
			break;
		
		default:
			System.out.println("Error: unrecognized opcode 0x" + Integer.toHexString(opcode));
			break;
		}
		
		//Only increment pc if the instruction was not a jump or subroutine call
		if ((opcode & 0xF000) != 0x1000 && 
			(opcode & 0xF000) != 0x2000 &&
			(opcode & 0xF000) != 0xB000)
		{
			pc += 2;
		}
		
		if (delay_timer > 0)
		{
			delay_timer--;
		}
		
		if (sound_timer > 0)
		{
			sound_timer--;
		}
	}
}
