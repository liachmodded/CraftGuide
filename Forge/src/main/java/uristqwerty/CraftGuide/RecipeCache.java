package uristqwerty.CraftGuide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import uristqwerty.CraftGuide.api.BasicRecipeFilter;
import uristqwerty.CraftGuide.api.CraftGuideRecipe;
import uristqwerty.CraftGuide.api.CraftGuideRecipeExtra1;
import uristqwerty.CraftGuide.api.ItemFilter;
import uristqwerty.CraftGuide.api.RecipeFilter;
import uristqwerty.CraftGuide.api.RecipeGenerator;
import uristqwerty.CraftGuide.api.RecipeProvider;
import uristqwerty.CraftGuide.api.SlotType;
import uristqwerty.CraftGuide.client.ui.IRecipeCacheListener;
import uristqwerty.CraftGuide.itemtype.ItemType;

public class RecipeCache
{
	private SortedSet<ItemType> craftingTypes = new TreeSet<ItemType>();
	private Map<ItemType, List<CraftGuideRecipe>> craftResults = new HashMap<ItemType, List<CraftGuideRecipe>>();
	private List<CraftGuideRecipe> typeResults;
	private List<CraftGuideRecipe> filteredResults;
	private RecipeGeneratorImplementation generator = new RecipeGeneratorImplementation();
	private ItemFilter filterItem = null;
	private List<IRecipeCacheListener> listeners = new LinkedList<IRecipeCacheListener>();
	private Set<ItemType> currentTypes = null;
	private SortedSet<ItemType> allItems = new TreeSet<ItemType>();
	private boolean firstReset = true;

	public RecipeCache()
	{
		reset();
	}

	public void reset()
	{
		CraftGuide.needsRecipeRefresh = false;

		CraftGuideLog.log("(re)loading recipe list...");
		Map<ItemStack, List<CraftGuideRecipe>> rawRecipes = generateRecipes();

		filterRawRecipes(rawRecipes);
		craftResults.clear();

		for(ItemStack key: rawRecipes.keySet())
		{
			ItemType type = ItemType.getInstance(key);

			if(type == null)
			{
				CraftGuideLog.log("  Error: null type, " + rawRecipes.get(key).size() + " recipes skipped");
				continue;
			}

			if(!craftResults.containsKey(type))
			{
				craftResults.put(type, new ArrayList<CraftGuideRecipe>());
			}

			craftResults.get(type).addAll(rawRecipes.get(key));
		}

		generator.clearRecipes();

		generateAllItemList();

		craftingTypes.addAll(craftResults.keySet());

		if(firstReset)
		{
			currentTypes = new HashSet<ItemType>();
			currentTypes.addAll(craftingTypes);

			for(ItemStack stack: generator.disabledTypes)
			{
				currentTypes.remove(ItemType.getInstance(stack));
			}

			firstReset = false;
		}

		setTypes(currentTypes);

		for(IRecipeCacheListener listener: listeners)
		{
			listener.onReset(this);
		}
	}

	private void generateAllItemList()
	{
		allItems.clear();

		for(List<CraftGuideRecipe> type: craftResults.values())
		{
			for(CraftGuideRecipe recipe: type)
			{
				for(Object item: recipe.getItems())
				{
					if(item != null)
					{
						if(item instanceof ItemStack)
						{
							allItems.add(ItemType.getInstance(item));
						}
						else if(item instanceof ArrayList)
						{
							for(ItemStack stack: (ArrayList<ItemStack>)item)
							{
								ItemType craftType = ItemType.getInstance(stack);

								if(craftType != null)
								{
									allItems.add(craftType);
								}
							}

							ItemType craftType = ItemType.getInstance(item);

							if(craftType != null)
							{
								allItems.add(craftType);
							}
						}
					}
				}
			}
		}

		removeUselessDuplicates();
	}

	private void removeUselessDuplicates()
	{
		HashMap<Item, Set<ItemType>> items = new HashMap<Item, Set<ItemType>>();

		for(ItemType type: allItems)
		{
			if(type.getStack() instanceof ItemStack)
			{
				Item item = ((ItemStack)type.getStack()).getItem();
				Set<ItemType> set = items.get(item);

				if(set == null)
				{
					set = new HashSet<ItemType>();
					items.put(item, set);
				}

				set.add(type);
			}
			else if(type.getStack() instanceof List)
			{
				for(Object o: (List<?>)type.getStack())
				{
					if(o instanceof ItemStack)
					{
						Item item = ((ItemStack)o).getItem();
						Set<ItemType> set = items.get(item);

						if(set == null)
						{
							set = new HashSet<ItemType>();
							items.put(item, set);
						}

						set.add(type);
					}
				}
			}
		}

		List<ItemType> toAdd = new ArrayList<ItemType>();

		for(Iterator<ItemType> i = allItems.iterator(); i.hasNext();)
		{
			ItemType type = i.next();

			if(type.getStack() instanceof ItemStack && CommonUtilities.getItemDamage((ItemStack)type.getStack()) == CraftGuide.DAMAGE_WILDCARD)
			{
				Item item = ((ItemStack)type.getStack()).getItem();
				Set<ItemType> set = items.get(item);

				if(set.size() == 1)
				{
					i.remove();
					toAdd.add(ItemType.getInstance(new ItemStack(item, 1, 0)));
				}
				else if(set.size() == 2)
				{
					i.remove();
				}
			}
		}

		allItems.addAll(toAdd);

		for(Iterator<ItemType> i = allItems.iterator(); i.hasNext();)
		{
			ItemType type = i.next();

			if(type.getStack() instanceof List && ((List<?>)type.getStack()).size() == 1 && ((List<?>)type.getStack()).get(0) instanceof ItemStack)
			{
				ItemStack stack = (ItemStack)((List<?>)type.getStack()).get(0);
				Item item = stack.getItem();
				Set<ItemType> set = items.get(item);
				boolean found = false;

				for(ItemType other: set)
				{
					if(other == type)
					{
						continue;
					}
					else if(other.getStack() instanceof ItemStack)
					{
						ItemStack otherStack = (ItemStack)other.getStack();
						if(ItemStack.areItemStacksEqual(stack, otherStack))
						{
							found = true;
							break;
						}
					}
				}

				if(found)
					i.remove();
			}
		}
	}

	private Map<ItemStack, List<CraftGuideRecipe>> generateRecipes()
	{
		generator.clearRecipes();
		CraftGuideLog.log("  Getting recipes...");
		for(Object object: ReflectionAPI.APIObjects)
		{
			if(object instanceof RecipeProvider)
			{
				if(CraftGuide.ae2Workaround && object instanceof RecipeGenerator)
				{
					CraftGuideLog.log("    *NOT* Generating recipes from " + object.getClass().getName());
					continue;
				}

				CraftGuideLog.log("    Generating recipes from " + object.getClass().getName());
				try
				{
					((RecipeProvider)object).generateRecipes(generator);
				}
				catch(Exception e)
				{
					CraftGuideLog.log(e);
				}
				catch(LinkageError e)
				{
					CraftGuideLog.log(e);
				}
			}
		}

		return generator.getRecipes();
	}

	private void filterRawRecipes(Map<ItemStack, List<CraftGuideRecipe>> rawRecipes)
	{
		CraftGuideLog.log("  Filtering recipes...");
		for(Object object: ReflectionAPI.APIObjects)
		{
			if(object instanceof RecipeFilter)
			{
				CraftGuideLog.log("    Filtering recipes from " + object.getClass().getName());

				try
				{
					for(ItemStack type: rawRecipes.keySet())
					{
						rawRecipes.put(type, ((RecipeFilter)object).filterRecipes(rawRecipes.get(type), type));
					}
				}
				catch(Exception e)
				{
					CraftGuideLog.log(e);
				}
				catch(LinkageError e)
				{
					CraftGuideLog.log(e);
				}
			}
			else if(object instanceof BasicRecipeFilter)
			{

				CraftGuideLog.log("    Filtering recipes from " + object.getClass().getName());

				try
				{
					for(ItemStack type: rawRecipes.keySet())
					{
						Iterator<CraftGuideRecipe> iterator = rawRecipes.get(type).iterator();

						while(iterator.hasNext())
						{
							if(!((BasicRecipeFilter)object).shouldKeepRecipe(iterator.next(), type))
							{
								iterator.remove();
							}
						}
					}
				}
				catch(Exception e)
				{
					CraftGuideLog.log(e);
				}
				catch(LinkageError e)
				{
					CraftGuideLog.log(e);
				}
			}
		}
	}

	public void setTypes(Set<ItemType> types)
	{
		typeResults = new ArrayList<CraftGuideRecipe>();
		currentTypes = types;

		if(types == null)
		{
			for(ItemType type: craftingTypes)
			{
				typeResults.addAll(craftResults.get(type));
			}
		}
		else
		{
			for(ItemType type: craftingTypes)
			{
				if(types.contains(type))
				{
					typeResults.addAll(craftResults.get(type));
				}
			}
		}

		filter(filterItem);
	}

	public List<CraftGuideRecipe> getRecipes()
	{
		return filteredResults;
	}

	public Map<ItemType, List<CraftGuideRecipe>> getAllRecipes()
	{
		return craftResults;
	}

	public void filter(ItemFilter filter)
	{
		if(filter instanceof NoItemFilter)
		{
			filter = null;
		}

		filterItem = filter;

		boolean input = GuiCraftGuide.filterSlotTypes.get(SlotType.INPUT_SLOT);
		boolean output = GuiCraftGuide.filterSlotTypes.get(SlotType.OUTPUT_SLOT);
		boolean machine = GuiCraftGuide.filterSlotTypes.get(SlotType.MACHINE_SLOT);

		if(filter == null)
		{
			filteredResults = typeResults;
		}
		else
		{
			filteredResults = new ArrayList<CraftGuideRecipe>();

			for(CraftGuideRecipe recipe: typeResults)
			{
				if(recipe instanceof CraftGuideRecipeExtra1)
				{
					CraftGuideRecipeExtra1 e = (CraftGuideRecipeExtra1)recipe;

					if((input && e.containsItem(filter, SlotType.INPUT_SLOT))
					|| (output && e.containsItem(filter, SlotType.OUTPUT_SLOT))
					|| (machine && e.containsItem(filter, SlotType.MACHINE_SLOT)))
					{
						filteredResults.add(recipe);
					}
				}
				else if(recipe.containsItem(filter))
				{
					filteredResults.add(recipe);
				}
			}
		}

		for(IRecipeCacheListener listener: listeners)
		{
			listener.onChange(this);
		}
	}

	public ItemFilter getFilter()
	{
		return filterItem;
	}

	public Set<ItemType> getCraftTypes()
	{
		return craftingTypes;
	}

	public void addListener(IRecipeCacheListener listener)
	{
		listeners.add(listener);
	}

	public SortedSet<ItemType> getAllItems()
	{
		return allItems;
	}

	public Set<ItemType> getFilterTypes()
	{
		return currentTypes;
	}
}
