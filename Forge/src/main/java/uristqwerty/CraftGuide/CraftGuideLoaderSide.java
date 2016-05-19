package uristqwerty.CraftGuide;

import java.io.File;

import net.minecraft.item.ItemStack;

public interface CraftGuideLoaderSide
{
	public boolean isModLoaded(String name);
	public File getConfigDir();
	public File getLogDir();
	public void addRecipe(ItemStack itemStack, Object[] objects);
	public void logConsole(String text);
	public void logConsole(String text, Throwable e);
	public void initClientNetworkChannels();
}
