## Market Basket Clustering using Cuisines Recipes Dataset

In this project, a method that combines text mining and clustering techniques is proposed as a solution to the 
problem of identifying types of customers based on the food they have bought given a dataset of market baskets and a 
dataset of recipes.

### Real data and baseline evaluations

To test this work over real datasets and to have a comparison with a baseline implementation, please refer to:   
```
    main.ipynb
```

### Generate new synthetic data

To generate new synthetic datasets, please refer to:   
```
     generateSynthBaskets.ipynb
```

### Scalability Evaluation with Synthetic Data

To test scalability, using ELKI as clustering algorithm, over synthetic data, please refer to:
```
     testSynthetic.ipynb
```

The clustering function in ELKI can be called using the provided script, here an example:

```
./dbscanELKI.sh \
    eps=0.1 minPoints=1000 \
    data=/home/nepotu/projects/dataMiningProject/data/basket_scores250000.csv \
    log=/home/nepotu/projects/dataMiningProject/data/log_250000.csv \
    output=/home/nepotu/projects/dataMiningProject/data/clusters_250000.csv
```