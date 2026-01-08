import numpy as np
import pandas as pd
import time
import os

# Определяем путь к папке, в которой лежит этот .py файл
# __file__ — это встроенная переменная, содержащая путь к текущему скрипту
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

def generate_nd_points(num_points: int, dimension: int, num_clusters: int = 4, filename: str = 'test_data.csv') -> pd.DataFrame:
    """
    Генерирует N-мерные точки, искусственно сгруппированные вокруг K центроидов.
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

    # Склеиваем путь к папке скрипта и имя файла
    full_path = os.path.join(SCRIPT_DIR, filename)
    df.to_csv(full_path, index=False)

    print(f"✅ Успешно сгенерировано {num_points} точек в {dimension}D пространстве.")
    print(f"Файл сохранен по пути: {full_path}")

    return df

def generate_statistical_points(
        num_points: int,
        dimension: int,
        range_start: float = 0.0,
        range_end: float = 100.0,
        filename: str = 'statistical_test.csv'
) -> pd.DataFrame:
    """
    Генерирует N-мерные точки, используя смесь Нормального и Равномерного распределений.
    """
    if dimension < 1:
        raise ValueError("Размерность должна быть больше 0.")

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
    df['cluster'] = 0

    # Склеиваем путь к папке скрипта и имя файла
    full_path = os.path.join(SCRIPT_DIR, filename)
    df.to_csv(full_path, index=False)

    print(f"\n✨ Успешно сгенерировано {num_points} точек в {dimension}D пространстве.")
    print(f"   Распределение: Равномерное ({uniform_dim}D) и Нормальное ({normal_dim}D).")
    print(f"   Файл сохранен по пути: {full_path}")

    return df

# Запуск генерации
df_result = generate_statistical_points(
    num_points=1_000_000_0,
    dimension=2,
    # Убираем лишние точки в имени файла, чтобы расширение было корректным
    filename=f'nd_clustering_test_1bil_{int(time.time())}.csv'
)

print("\nПервые 5 строк сгенерированных данных:")
print(df_result.head())