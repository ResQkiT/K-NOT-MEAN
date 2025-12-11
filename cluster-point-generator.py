import numpy as np
import pandas as pd
import time

def generate_nd_points(num_points: int, dimension: int, num_clusters: int = 4, filename: str = 'test_data.csv') -> pd.DataFrame:
    """
    Генерирует N-мерные точки, искусственно сгруппированные вокруг K центроидов.

    :param num_points: Общее количество точек (M).
    :param dimension: Размерность пространства (N).
    :param num_clusters: Количество искусственных кластеров (K).
    :param filename: Имя файла для сохранения CSV.
    :return: DataFrame с данными и метками кластеров.
    """
    if num_clusters > num_points:
        raise ValueError("Количество кластеров не может превышать количество точек.")

    centroids = np.random.uniform(0, 10, size=(num_clusters, dimension))

    data = []
    labels = []

    for _ in range(num_points):
        cluster_id = np.random.randint(num_clusters)
        center = centroids[cluster_id]

        noise = np.random.uniform(-0.8, 0.8, dimension)

        point = center + noise

        data.append(point)
        labels.append(cluster_id)

    column_names = [f'dim{i+1}' for i in range(dimension)]
    df = pd.DataFrame(data, columns=column_names)

    df['cluster'] = labels

    df.to_csv(filename, index=False)
    print(f"✅ Успешно сгенерировано {num_points} точек в {dimension}D пространстве.")
    print(f"Файл сохранен как '{filename}'")

    return df

def generate_statistical_points(
    num_points: int, 
    dimension: int, 
    range_start: float = 0.0, 
    range_end: float = 100.0, 
    filename: str = 'statistical_test.csv'
) -> pd.DataFrame:
    """
    Генерирует N-мерные точки, используя смесь Нормального и Равномерного распределений,
    для стресс-тестирования кластеризации. Все точки помечаются кластером -1.

    :param num_points: Общее количество точек (M).
    :param dimension: Размерность пространства (N).
    :param range_start: Нижняя граница диапазона генерации.
    :param range_end: Верхняя граница диапазона генерации.
    :param filename: Имя файла для сохранения CSV.
    :return: DataFrame с данными и метками кластеров (-1).
    """
    if dimension < 1:
        raise ValueError("Размерность должна быть больше 0.")
        
    # Разделяем измерения на Равномерное и Нормальное распределения (примерно 50/50)
    uniform_dim = dimension // 2
    normal_dim = dimension - uniform_dim
    
    if uniform_dim > 0:
        uniform_data = np.random.uniform(range_start, range_end, size=(num_points, uniform_dim))
    else:
        uniform_data = np.empty((num_points, 0))

    if normal_dim > 0:
        mean = (range_start + range_end) / 2
        std_dev = (range_end - range_start) / 6 
        
        normal_data = np.random.normal(mean, std_dev, size=(num_points, normal_dim))
    else:
        normal_data = np.empty((num_points, 0))

    data = np.hstack((uniform_data, normal_data))
    
    column_names = [f'dim{i+1}' for i in range(dimension)]
    df = pd.DataFrame(data, columns=column_names)
    
    df['cluster'] = -1
    
    df.to_csv(filename, index=False)
    print(f"\n✨ Успешно сгенерировано {num_points} точек в {dimension}D пространстве.")
    print(f"   Распределение: Равномерное ({uniform_dim}D) и Нормальное ({normal_dim}D).")
    print(f"   Файл сохранен как '{filename}'")
    
    return df

df_result = generate_statistical_points(
    num_points=100000,
    dimension=2,
    filename='nd_clustering_test'+ str(time.time()) + '.csv'
)

print("\nПервые 5 строк сгенерированных данных:")
print(df_result.head())