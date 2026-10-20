package io.github.mgthorn.shoppingmaterialist.client.trading;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the anvil combining order that costs the fewest levels when putting books on an item.
 * <p>
 * Anvil rules (vanilla): combining a target with a book costs the sum of the book's enchantments
 * ({@code level * max(1, anvilCost / 2)}) plus the prior work penalty {@code 2^n - 1} of both inputs,
 * where n is how often an input went through an anvil. The result's n is {@code max(nA, nB) + 1}.
 * In survival a step costing 40 or more levels is "Too Expensive!".
 * <p>
 * Uses dynamic programming over all subsets of the books, so any merge tree (books combined with each other first,
 * then with the item) is considered.
 */
public class AnvilOptimizer {
	public static final int TOO_EXPENSIVE = 40;
	// Above this many books the search gets slow (3^n); the books are then applied one by one
	private static final int MAX_BOOKS = 10;

	/**
	 * One anvil step: {@code target} (left slot) + {@code sacrifice} (right slot).
	 * Both are bit masks of the inputs; bit 0 is the item, bit i the book i - 1.
	 */
	public record Step(int target, int sacrifice, int cost) {
	}

	public record Result(List<Step> steps, int totalLevels, boolean tooExpensive) {
	}

	/**
	 * @param bookValues the level cost of each book when used as the sacrifice
	 * @param bookWork how often each book already went through an anvil (books combined from lower levels)
	 * @param itemWork how often the item already went through an anvil
	 */
	public static Result optimize(int[] bookValues, int[] bookWork, int itemWork) {
		int n = bookValues.length;

		if (n == 0) {
			return new Result(List.of(), 0, false);
		}

		if (n > MAX_BOOKS) {
			return applyOneByOne(bookValues, bookWork, itemWork);
		}

		Result result = solve(bookValues, bookWork, itemWork, true);
		return result != null ? result : solve(bookValues, bookWork, itemWork, false);
	}

	/**
	 * @param limit only allow steps below {@link #TOO_EXPENSIVE}
	 * @return null if there is no solution within the limit
	 */
	private static Result solve(int[] bookValues, int[] bookWork, int itemWork, boolean limit) {
		int n = bookValues.length;
		int elements = n + 1;
		int full = (1 << elements) - 1;
		int maxWork = Math.max(itemWork, Arrays.stream(bookWork).max().orElse(0)) + elements;

		// Sum of the book values in each mask (the item has no value as it is never a sacrifice)
		int[] value = new int[full + 1];

		for (int mask = 1; mask <= full; mask++) {
			int low = Integer.numberOfTrailingZeros(mask);
			value[mask] = value[mask & (mask - 1)] + (low == 0 ? 0 : bookValues[low - 1]);
		}

		// best[mask][work] = fewest total levels to merge the inputs in mask into one with that prior work
		int[][] best = new int[full + 1][maxWork + 1];
		int[][] splitTarget = new int[full + 1][maxWork + 1];
		int[][] splitTargetWork = new int[full + 1][maxWork + 1];
		int[][] splitSacrificeWork = new int[full + 1][maxWork + 1];

		for (int[] row : best) {
			Arrays.fill(row, Integer.MAX_VALUE);
		}

		best[1][itemWork] = 0;

		for (int i = 1; i <= n; i++) {
			best[1 << i][bookWork[i - 1]] = 0;
		}

		// Masks in increasing order, so all sub-masks are solved first
		for (int mask = 1; mask <= full; mask++) {
			if (Integer.bitCount(mask) < 2) {
				continue;
			}

			for (int target = (mask - 1) & mask; target > 0; target = (target - 1) & mask) {
				int sacrifice = mask & ~target;

				// The item always stays in the left slot
				if ((sacrifice & 1) != 0) {
					continue;
				}

				for (int wt = 0; wt <= maxWork; wt++) {
					if (best[target][wt] == Integer.MAX_VALUE) {
						continue;
					}

					for (int ws = 0; ws <= maxWork; ws++) {
						if (best[sacrifice][ws] == Integer.MAX_VALUE) {
							continue;
						}

						int stepCost = value[sacrifice] + penalty(wt) + penalty(ws);

						if (limit && stepCost >= TOO_EXPENSIVE) {
							continue;
						}

						int work = Math.max(wt, ws) + 1;
						int total = best[target][wt] + best[sacrifice][ws] + stepCost;

						if (work <= maxWork && total < best[mask][work]) {
							best[mask][work] = total;
							splitTarget[mask][work] = target;
							splitTargetWork[mask][work] = wt;
							splitSacrificeWork[mask][work] = ws;
						}
					}
				}
			}
		}

		int bestWork = -1;

		for (int w = 0; w <= maxWork; w++) {
			if (best[full][w] != Integer.MAX_VALUE && (bestWork < 0 || best[full][w] < best[full][bestWork])) {
				bestWork = w;
			}
		}

		if (bestWork < 0) {
			return null;
		}

		List<Step> steps = new ArrayList<>();
		collectSteps(full, bestWork, value, splitTarget, splitTargetWork, splitSacrificeWork, steps);

		boolean tooExpensive = steps.stream().anyMatch(step -> step.cost() >= TOO_EXPENSIVE);
		return new Result(steps, best[full][bestWork], tooExpensive);
	}

	// Children first, so the steps are in the order they have to be done
	private static void collectSteps(int mask, int work, int[] value, int[][] splitTarget, int[][] splitTargetWork,
			int[][] splitSacrificeWork, List<Step> steps) {
		if (Integer.bitCount(mask) < 2) {
			return;
		}

		int target = splitTarget[mask][work];
		int sacrifice = mask & ~target;
		int wt = splitTargetWork[mask][work];
		int ws = splitSacrificeWork[mask][work];

		collectSteps(target, wt, value, splitTarget, splitTargetWork, splitSacrificeWork, steps);
		collectSteps(sacrifice, ws, value, splitTarget, splitTargetWork, splitSacrificeWork, steps);
		steps.add(new Step(target, sacrifice, value[sacrifice] + penalty(wt) + penalty(ws)));
	}

	private static Result applyOneByOne(int[] bookValues, int[] bookWork, int itemWork) {
		List<Step> steps = new ArrayList<>();
		int mask = 1;
		int work = itemWork;
		int total = 0;

		for (int i = 0; i < bookValues.length; i++) {
			int cost = bookValues[i] + penalty(work) + penalty(bookWork[i]);
			steps.add(new Step(mask, 1 << (i + 1), cost));
			mask |= 1 << (i + 1);
			work = Math.max(work, bookWork[i]) + 1;
			total += cost;
		}

		boolean tooExpensive = steps.stream().anyMatch(step -> step.cost() >= TOO_EXPENSIVE);
		return new Result(steps, total, tooExpensive);
	}

	private static int penalty(int work) {
		return (1 << work) - 1;
	}
}
