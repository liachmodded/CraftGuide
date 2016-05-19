package uristqwerty.CraftGuide;

import java.io.File;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.gameevent.TickEvent;

@Mod(modid = "craftguide", name = "CraftGuide", version = "@MOD_VERSION@")
public class CraftGuide_FML implements CraftGuideLoaderSide
{
	private static Logger logger;

	@SidedProxy(clientSide = "uristqwerty.CraftGuide.client.fml.CraftGuideClient_FML",
				serverSide = "uristqwerty.CraftGuide.server.CraftGuideServer")
	public static CraftGuideSide side;

	private CraftGuide craftguide;

	public static class KeyCheckTick
	{
		@SubscribeEvent
		public void clientTick(TickEvent.ClientTickEvent event)
		{
			side.checkKeybind();
		}
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		logger = event.getModLog();
		CraftGuide.loaderSide = this;
		CraftGuide.side = side;
		craftguide = new CraftGuide();
		craftguide.preInit("craftguide:craftguide_item", false);

		FMLCommonHandler.instance().bus().register(new KeyCheckTick());
	}

	@EventHandler
	public void init(FMLInitializationEvent event)
	{
		craftguide.init();
	}



	@Override
	public boolean isModLoaded(String name)
	{
		return Loader.isModLoaded(name);
	}

	@Override
	public File getConfigDir()
	{
		return Loader.instance().getConfigDir();
	}

	@Override
	public File getLogDir()
	{
		File mcDir = null;
		try
		{
			mcDir = (File)CommonUtilities.getPrivateValue(Loader.class, null, "minecraftDir");
		}
		catch(SecurityException e)
		{
			CraftGuideLog.log(e, "", true);
		}
		catch(IllegalArgumentException e)
		{
			CraftGuideLog.log(e, "", true);
		}
		catch(NoSuchFieldException e)
		{
			CraftGuideLog.log(e, "", true);
		}
		catch(IllegalAccessException e)
		{
			CraftGuideLog.log(e, "", true);
		}

		if(mcDir == null)
			return CraftGuide.configDirectory();

		File dir = new File(mcDir, "logs");

		if(!dir.exists() && !dir.mkdirs())
		{
			return CraftGuide.configDirectory();
		}

		return dir;
	}

	@Override
	public void addRecipe(ItemStack output, Object[] recipe)
	{
		GameRegistry.addRecipe(output, recipe);
	}

	@Override
	public void logConsole(String text)
	{
		logger.log(Level.INFO, text);
	}

	@Override
	public void logConsole(String text, Throwable e)
	{
		logger.log(Level.WARN, text, e);
	}

	@Override
	public void initClientNetworkChannels()
	{
		// TODO Auto-generated method stub

	}
}
