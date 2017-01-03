/**
 * A work-in-progress emulator for the CHIP-8 interpreted programming language.
 * CHIP-8 was used in computers in the 70s to run classic video games including Pong, Space Invaders, and Tetris.
 * 
 * Made using CowGod's CHIP-8 Technical Reference:  http://devernay.free.fr/hacks/chip8/C8TECH10.HTM
 * 
 * @author Amil Mahida
 *
 */

public class Driver
{
	/**
	 * Currently, all this does is attempt to open and read the ROM file specified.
	 * @param args
	 */
	public static void main(String[] args)
	{
		Chip8Core c8 = new Chip8Core();
		String filename = "PONG";
		
		c8.init();
		c8.open_file(filename);
		
		System.out.println("Success");
	}

}
