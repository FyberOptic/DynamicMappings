package net.fybertech.dynamicmappings;

import java.io.File;
import java.util.List;

import net.fybertech.meddle.MeddleMod;
import net.fybertech.meddle.MeddleUtil;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;


@MeddleMod(id="dynamicmappings", name="Dynamic Mappings", author="FyberOptic", version="015-alpha")
public class Tweaker implements ITweaker
{

	@Override
	public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile)
	{
		DynamicMappings.generateClassMappings();
	}

	@Override
	public void injectIntoClassLoader(LaunchClassLoader classLoader) {}

	@Override
	public String getLaunchTarget() {
		return null;
	}

	@Override
	public String[] getLaunchArguments() {
		return new String[0];
	}

}
