import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class Chip8Core
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
	short display_timer = 0;
	
	boolean[][] display = new boolean[64][32];  //Screen display (each pixel is either on or off)
	boolean[][] keypad = new boolean[4][4];  //16-button keypad (each button is either pressed or not)
	
	short opcode = 0;
	
	void init()
	{
		/*Originally, the CHIP-8 interpreter would be stored in the first 512 bytes of memory
		 * but we don't need that so we can just store font data in there instead.
		 */
		for (int i = 0; i < chip8_fontset.length; i++)
		{
			mem[i] = chip8_fontset[i];
		}
	}
	
	void open_file(String filename)
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
		
		switch (opcode & 0xF000)
		{
		case 0x0000:
			switch (opcode & 0x00FF)
			{
			case 0xE0:  //00E0 - clear the screen
				for(boolean[] row : display)
				{
					Arrays.fill(row, false);
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
			if (V[(opcode & 0x0F00) >> 8] == (opcode & 0xFF))
			{
				pc += 2;
			}
			break;
		
		case 0x4000:  //4XNN - if Vx != NN, skip the next instruction
			if (V[(opcode & 0x0F00) >> 8] != (opcode & 0xFF))
			{
				pc += 2;
			}
			break;
		
		case 0x5000:  //5XY0 - if Vx == Vy, skip the next instruction
			if (V[(opcode & 0x0F00) >> 8] == V[(opcode & 0x00F0) >> 4])
			{
				pc += 2;
			}
			break;
			
		case 0x6000:  //6XNN - set Vx to NN
			V[(opcode & 0x0F00) >> 8] = (short) (opcode & 0xFF);
			break;
			
		case 0x7000:  //7XNN - add NN to Vx
			V[(opcode & 0x0F00) >> 8] += (short) (opcode & 0xFF);
			break;
			
		case 0x8000:
			switch (opcode & 0xF)
			{
			case 0x0:  //8XY0 - set Vx equal to Vy
				V[(opcode & 0x0F00) >> 8] = V[(opcode & 0x00F0) >> 4];
				break;
				
			case 0x1:  //8XY1 - set Vx equal to Vx BITWISE OR Vy
				V[(opcode & 0x0F00) >> 8] = (short) (V[(opcode & 0x0F00) >> 8] | V[(opcode & 0x00F0) >> 4]);
				break;
				
			case 0x2:  //8XY2 - set Vx equal to Vx BITWISE AND Vy
				V[(opcode & 0x0F00) >> 8] = (short) (V[(opcode & 0x0F00) >> 8] & V[(opcode & 0x00F0) >> 4]);
				break;
				
			case 0x3:  //8XY3 - set Vx equal to Vx BITWISE XOR Vy
				V[(opcode & 0x0F00) >> 8] = (short) (V[(opcode & 0x0F00) >> 8] ^ V[(opcode & 0x00F0) >> 4]);
				break;
				
			case 0x4:  //8XY4 - set Vx equal to the last 8 bits of Vx + Vy, set V[F] to 1 if the result is greater than 255
				V[(opcode & 0x0F00) >> 8] = (short) (V[(opcode & 0x0F00) >> 8] + V[(opcode & 0x00F0) >> 4]);
				if (V[(opcode & 0x0F00) >> 8] > 255)
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[(opcode & 0x0F00) >> 8] = (short) (V[(opcode & 0x0F00) >> 8] & 0xFF);
				break;
				
			case 0x5:  //8XY5 - set Vx equal to Vx - Vy, set V[F} to 1 if Vx > Vy
				if (V[(opcode & 0x0F00) >> 8] > V[(opcode & 0x00F0) >> 4])
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[(opcode & 0x0F00) >> 8] = (short) (V[(opcode & 0x0F00) >> 8] - V[(opcode & 0x00F0) >> 4]);
			}
			break;
		
		default:
			System.out.println("Error: unrecognized opcode 0x" + Integer.toHexString(opcode));
			break;
		}
		
		//Only increment pc if the instruction was not a jump or subroutine call
		if ((opcode & 0xF000) != 0x1000 && 
			(opcode & 0xF000) != 0x2000)
		{
			pc += 2;
		}
		
		if (display_timer > 0)
		{
			display_timer--;
		}
		
		if (sound_timer > 0)
		{
			sound_timer--;
		}
	}
}
