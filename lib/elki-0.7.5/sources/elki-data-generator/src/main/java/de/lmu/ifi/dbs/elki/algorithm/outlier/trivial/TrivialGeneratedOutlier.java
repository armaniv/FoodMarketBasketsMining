/*
 * This file is part of ELKI:
 * Environment for Developing KDD-Applications Supported by Index-Structures
 *
 * Copyright (C) 2019
 * ELKI Development Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package de.lmu.ifi.dbs.elki.algorithm.outlier.trivial;

import java.util.HashSet;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.outlier.OutlierAlgorithm;
import de.lmu.ifi.dbs.elki.data.NumberVector;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.data.synthetic.bymodel.GeneratorSingleCluster;
import de.lmu.ifi.dbs.elki.data.type.NoSupportedDataTypeException;
import de.lmu.ifi.dbs.elki.data.type.SimpleTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreFactory;
import de.lmu.ifi.dbs.elki.database.datastore.DataStoreUtil;
import de.lmu.ifi.dbs.elki.database.datastore.WritableDoubleDataStore;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.relation.DoubleRelation;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedDoubleRelation;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.ChiSquaredDistribution;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.Distribution;
import de.lmu.ifi.dbs.elki.math.statistics.distribution.NormalDistribution;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierResult;
import de.lmu.ifi.dbs.elki.result.outlier.OutlierScoreMeta;
import de.lmu.ifi.dbs.elki.result.outlier.ProbabilisticOutlierScore;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.CommonConstraints;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;

/**
 * Extract outlier score from the model the objects were generated by.
 * 
 * This algorithm can only be applied to data that was freshly generated, to the
 * generator model information is still available.
 * 
 * @author Erich Schubert
 * @since 0.5.0
 */
public class TrivialGeneratedOutlier extends AbstractAlgorithm<OutlierResult> implements OutlierAlgorithm {
  /**
   * Class logger
   */
  private static final Logging LOG = Logging.getLogger(TrivialGeneratedOutlier.class);

  /**
   * Expected share of outliers.
   */
  double expect = 0.01;

  /**
   * Constructor.
   * 
   * @param expect Expected share of outliers
   */
  public TrivialGeneratedOutlier(double expect) {
    super();
    this.expect = expect;
  }

  /**
   * Constructor.
   */
  public TrivialGeneratedOutlier() {
    this(0.01);
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(TypeUtil.NUMBER_VECTOR_FIELD, new SimpleTypeInformation<>(Model.class), TypeUtil.GUESSED_LABEL);
  }

  @Override
  public OutlierResult run(Database database) {
    Relation<NumberVector> vecs = database.getRelation(TypeUtil.NUMBER_VECTOR_FIELD);
    Relation<Model> models = database.getRelation(new SimpleTypeInformation<>(Model.class));
    // Prefer a true class label
    try {
      Relation<?> relation = database.getRelation(TypeUtil.CLASSLABEL);
      return run(models, vecs, relation);
    }
    catch(NoSupportedDataTypeException e) {
      // Otherwise, try any labellike.
      return run(models, vecs, database.getRelation(TypeUtil.GUESSED_LABEL));
    }
  }

  /**
   * Run the algorithm
   * 
   * @param models Model relation
   * @param vecs Vector relation
   * @param labels Label relation
   * @return Outlier result
   */
  public OutlierResult run(Relation<Model> models, Relation<NumberVector> vecs, Relation<?> labels) {
    WritableDoubleDataStore scores = DataStoreUtil.makeDoubleStorage(models.getDBIDs(), DataStoreFactory.HINT_HOT);

    HashSet<GeneratorSingleCluster> generators = new HashSet<>();
    for(DBIDIter iditer = models.iterDBIDs(); iditer.valid(); iditer.advance()) {
      Model model = models.get(iditer);
      if(model instanceof GeneratorSingleCluster) {
        generators.add((GeneratorSingleCluster) model);
      }
    }
    if(generators.isEmpty()) {
      LOG.warning("No generator models found for dataset - all points will be considered outliers.");
    }
    for(GeneratorSingleCluster gen : generators) {
      for(int i = 0; i < gen.getDim(); i++) {
        Distribution dist = gen.getDistribution(i);
        if(!(dist instanceof NormalDistribution)) {
          throw new AbortException("TrivialGeneratedOutlier currently only supports normal distributions, got: " + dist);
        }
      }
    }

    for(DBIDIter iditer = models.iterDBIDs(); iditer.valid(); iditer.advance()) {
      double score = 1.;
      double[] v = vecs.get(iditer).toArray();
      for(GeneratorSingleCluster gen : generators) {
        double[] tv = v;
        // Transform backwards
        if(gen.getTransformation() != null) {
          tv = gen.getTransformation().applyInverse(v);
        }
        final int dim = tv.length;
        double lensq = 0.0;
        int norm = 0;
        for(int i = 0; i < dim; i++) {
          Distribution dist = gen.getDistribution(i);
          if(dist instanceof NormalDistribution) {
            NormalDistribution d = (NormalDistribution) dist;
            double delta = (tv[i] - d.getMean()) / d.getStddev();
            lensq += delta * delta;
            norm += 1;
          }
          else {
            throw new AbortException("TrivialGeneratedOutlier currently only supports normal distributions, got: " + dist);
          }
        }
        if(norm > 0.) {
          // The squared distances are ChiSquared distributed
          score = Math.min(score, ChiSquaredDistribution.cdf(lensq, norm));
        }
        else {
          score = 0.;
        }
      }
      if(expect < 1) {
        score = expect * score / (1 - score + expect);
      }
      scores.putDouble(iditer, score);
    }
    DoubleRelation scoreres = new MaterializedDoubleRelation("Model outlier scores", "model-outlier", scores, models.getDBIDs());
    OutlierScoreMeta meta = new ProbabilisticOutlierScore(0., 1.);
    return new OutlierResult(meta, scoreres);
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   */
  public static class Parameterizer extends AbstractParameterizer {
    /**
     * Expected share of outliers
     */
    public static final OptionID EXPECT_ID = new OptionID("modeloutlier.expect", "Expected amount of outliers, for making the scores more intuitive. When the value is 1, the CDF will be given instead.");

    /**
     * Expected share of outliers
     */
    double expect;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      DoubleParameter expectP = new DoubleParameter(EXPECT_ID, 0.01) //
          .addConstraint(CommonConstraints.GREATER_THAN_ZERO_DOUBLE) //
          .addConstraint(CommonConstraints.LESS_EQUAL_ONE_DOUBLE);
      if(config.grab(expectP)) {
        expect = expectP.getValue();
      }
    }

    @Override
    protected TrivialGeneratedOutlier makeInstance() {
      return new TrivialGeneratedOutlier(expect);
    }
  }
}
