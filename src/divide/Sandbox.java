package divide;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Sandbox
{
	/**
	 * @param <V>
	 *            variable type
	 * @param <R>
	 *            range type
	 */
	static abstract class ProblemGraph< V, R >
	{
		public abstract Set< V > allVariables();

		public abstract Map< V, R > solve( final Set< V > region );

		public abstract Set< V > gamma( final Set< V > region );

		Set< V > gamma( final int n, final Set< V > region )
		{
			if ( n == 1 )
				return gamma( region );
			else
				return gamma( n - 1, region );
		}

		Map< V, R > restrict( final Map< V, R > assignment, final Set< V > region )
		{
			final HashMap< V, R > restriction = new HashMap< V, R >();
			for ( final V v : region )
				if ( assignment.containsKey( v ) )
					restriction.put( v, assignment.get( v ) );
			return restriction;
		}
	}

	public interface KappaUpdateFunction
	{
		public int next( int kappa );
	}

	public static < V, R > Map< V, R > solve( final ProblemGraph< V, R > problem, final int kappaStart, final KappaUpdateFunction u )
	{
		final Set< V > variables = problem.allVariables();
		final Set< V > conflicts = new HashSet< V >( variables );
		final Map< V, Integer > kappas = new HashMap< V, Integer >();
		for ( final V v : variables )
			kappas.put( v, kappaStart );
		final Map< V, Map< V, R > > solutions = new HashMap< V, Map< V, R > >();

		while ( !conflicts.isEmpty() )
		{
			final V v = conflicts.iterator().next();
			conflicts.remove( v );

			final Set< V > gammaV = problem.gamma( Collections.singleton( v ) );

			// check whether the conflict still persists
			boolean hasConflict = false;
			for ( final V vprime : gammaV )
				if ( vprime.equals( v ) )
					continue;
				else if ( !areConsistent( solutions.get( v ), solutions.get( vprime ) ) )
				{
					hasConflict = true;
					break;
				}
			if ( !hasConflict )
				continue;

			final int kappa = kappas.get( v );

			final Map< V, R > sigma = problem.restrict( problem.solve( problem.gamma( kappa, gammaV ) ), gammaV );

			solutions.put( v, sigma );

			for ( final V vprime : gammaV )
				if ( vprime.equals( v ) )
					continue;
				else if ( !areConsistent( sigma, solutions.get( vprime ) ) )
				{
					conflicts.add( vprime );
					kappas.put( vprime, Math.max( kappa, u.next( kappas.get( vprime ) ) ) );
				}
		}

		final Map< V, R > globalSolution = new HashMap< V, R >();
		for ( final Map< V, R > s : solutions.values() )
			for ( final Entry< V, R > entry : s.entrySet() )
				globalSolution.put( entry.getKey(), entry.getValue() );

		return globalSolution;
	}

	public static < V, R > boolean areConsistent( final Map< V, R > assignment, final Map< V, R > otherAssignment )
	{
		if ( assignment == null || otherAssignment == null )
			return false;
		for ( final Entry< V, R > entry : assignment.entrySet() )
			if ( otherAssignment.containsKey( entry.getKey() ) && !otherAssignment.get( entry.getKey() ).equals( entry.getValue() ) )
				return false;
		return true;
	}

	public static void main( final String[] args )
	{}
}
